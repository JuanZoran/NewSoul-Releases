import sys, tempfile, unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from telegram_feedback import Relay, ApiError

class Fake:
    def __init__(self): self.calls=[]; self.fail=False; self.seq=100
    def __call__(self, method, **fields):
        self.calls.append((method,fields))
        if self.fail and method=='copyMessage': raise ApiError()
        self.seq+=1
        return {'message_id':self.seq}

def message(uid, peer=2, text='problem', **kwargs):
    msg=dict(message_id=uid, chat=dict(id=peer,type='private'), **{'from':dict(id=peer,is_bot=False)})
    if text is not None: msg['text']=text
    msg.update(kwargs)
    return dict(update_id=uid,message=msg)

class Tests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.api=Fake()
        self.path=Path(self.tmp.name)/'state.db'; self.r=Relay(self.path,1,self.api); self.r.bind(99)
    def tearDown(self): self.r.db.close(); self.tmp.cleanup()
    def test_private_routes_and_owner_reply(self):
        self.r.handle(message(1)); self.r.handle(message(2,1,'reply',reply_to_message={'message_id':101}))
        copies=[f for m,f in self.api.calls if m=='copyMessage']
        self.assertEqual([f['chat_id'] for f in copies],[1,2])
        self.assertEqual(copies[1]['reply_parameters']['message_id'],1)
    def test_media_routes(self):
        for i,k in enumerate(['photo','document','video','voice'],1): self.r.handle(message(i,2,None,**{k:[]}))
        self.assertEqual(sum(m=='copyMessage' for m,_ in self.api.calls),4)
    def test_groups_and_bots_ignored(self):
        u=message(1);u['message']['chat']['type']='group';self.r.handle(u)
        u=message(2);u['message']['from']['is_bot']=True;self.r.handle(u)
        self.assertFalse(self.api.calls)
    def test_spoofed_owner_and_unknown_reply(self):
        u=message(1,1);u['message']['from']['id']=2;self.r.handle(u)
        self.r.handle(message(2,1,reply_to_message={'message_id':999}))
        self.assertFalse(any(m=='copyMessage' for m,_ in self.api.calls))
    def test_untrusted_reply_cannot_route(self):
        self.r.handle(message(1,2,reply_to_message={'message_id':101,'forward_from':{'id':3}}))
        self.assertEqual(self.api.calls[0][1]['chat_id'],1)
    def test_duplicate_after_restart(self):
        self.r.handle(message(1)); n=len(self.api.calls);self.r.db.close();self.r=Relay(self.path,1,self.api)
        self.r.handle(message(1));self.assertEqual(len(self.api.calls),n)
        self.assertEqual(self.r.meta('offset'),'2')
    def test_uncertain_send_not_retried(self):
        self.api.fail=True;self.r.handle(message(1));n=len(self.api.calls)
        self.r.handle(message(1));self.assertEqual(len(self.api.calls),n)
        self.assertEqual(self.r.db.execute('SELECT state FROM updates').fetchone()[0],'uncertain')
    def test_owner_block_and_unblock(self):
        self.r.handle(message(1));self.r.handle(message(2,1,'/block',reply_to_message={'message_id':101}))
        n=len(self.api.calls);self.r.handle(message(3));self.assertEqual(len(self.api.calls),n)
        self.r.handle(message(4,1,'/unblock',reply_to_message={'message_id':101}));self.r.handle(message(5))
        self.assertEqual(sum(m=='copyMessage' for m,_ in self.api.calls),2)
    def test_identity_lock(self):
        with self.assertRaises(ValueError): self.r.bind(100)
    def test_start_not_relayed(self):
        self.r.handle(message(1,2,'/start feedback'));self.assertEqual(len(self.api.calls),1)
        self.assertEqual(self.api.calls[0][1]['chat_id'],2)
    def test_status_excludes_its_own_processing_record(self):
        self.r.handle(message(1,1,'/status'))
        self.assertIn('待核对记录：0', self.api.calls[-1][1]['text'])
    def test_body_not_stored(self):
        self.r.handle(message(1,text='private-secret-body'))
        self.assertNotIn('private-secret-body','\n'.join(self.r.db.iterdump()))
    def test_crash_intent_not_replayed(self):
        self.r.db.execute("INSERT INTO updates VALUES (1,'processing')");self.r.db.commit()
        self.r.handle(message(1));self.assertFalse(self.api.calls)

if __name__=='__main__': unittest.main()
