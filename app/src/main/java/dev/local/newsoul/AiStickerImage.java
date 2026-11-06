package dev.local.newsoul;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

/** Downloads selected stickers only; preserves original GIF/WebP animation bytes. */
final class AiStickerImage {
    static AiReplyClient.ImageInput prepare(String url,AiReplyClient.Cancellation cancel) throws Exception {
        if(AiContextRules.ordinaryImageUrl(url)==null)throw new IllegalArgumentException("Unsupported sticker URL");
        HttpsURLConnection connection=(HttpsURLConnection)new URL(url).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(8000);connection.setReadTimeout(8000);
            cancel.attach(connection);
            if(connection.getResponseCode()!=200)throw new IllegalStateException("Sticker unavailable");
            if(connection.getContentLengthLong()>8*1024*1024)throw new IllegalStateException("Sticker too large");
            byte[] bytes;
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] chunk=new byte[8192];int n;
                while((n=in.read(chunk))!=-1){cancel.check();if(out.size()+n>8*1024*1024)throw new IllegalStateException("Sticker too large");out.write(chunk,0,n);}
                bytes=out.toByteArray();
            }
            cancel.check();
            return AiReplyClient.ImageInput.original(bytes);
        } finally {
            cancel.detach(connection);connection.disconnect();
        }
    }
}
