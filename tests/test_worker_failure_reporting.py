"""Force production test workers to fail and require their runners to fail too."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class WorkerFailureReportingTest(unittest.TestCase):
    def test_worker_assertions_and_operation_errors_fail_runners(self):
        cases = (
            ('menu-fallback', 'ChatMenuFallbackTest',
             'check(ChatMenuContext.takeRenderer()==null,"thread isolation")',
             'check(false,"AUDIT_FORCED_WORKER_FAILURE")'),
            ('preference-changes', 'PreferenceChangesTest',
             'check(!PreferenceChanges.write(safe,\n'
             '                Map.of("square_auto_like", true, "keep", "imported")), "concurrent import fails")',
             'check(PreferenceChanges.write(safe,\n'
             '                Map.of("square_auto_like", true, "keep", "imported")), "AUDIT_FORCED_WORKER_FAILURE")'),
            ('chat-menu', 'ChatMenuDiagnosticsTest',
             'other[0] = scope.trace.id;',
             'throw new AssertionError("AUDIT_FORCED_WORKER_FAILURE");'),
        )
        for runner, name, before, after in cases:
            with self.subTest(runner=runner), tempfile.TemporaryDirectory(prefix='newsoul-worker-failure-') as folder:
                source = (ROOT / 'tests' / (name + '.java')).read_text()
                self.assertEqual(source.count(before), 1, 'failure injection target changed')
                target = Path(folder) / (name + '.java')
                target.write_text(source.replace(before, after))
                script = (ROOT / 'tests' / ('run-' + runner + '-tests.sh')).read_text()
                script = script.replace('cd "$(dirname "$0")/.."', '')
                script = script.replace('tests/' + name + '.java', str(target))
                result = subprocess.run(['bash', '-c', script], cwd=ROOT,
                                        capture_output=True, text=True, timeout=90)
                self.assertIn('AUDIT_FORCED_WORKER_FAILURE', result.stderr,
                              result.stdout + result.stderr)
                self.assertNotEqual(result.returncode, 0, 'worker failure was falsely accepted')


if __name__ == '__main__':
    unittest.main()
