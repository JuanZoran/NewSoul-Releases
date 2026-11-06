package dev.local.newsoul;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Shared by Android and offline checks, so tests load the exact packaged runtime rules. */
final class AiReplyPrompt {
    static final String RESOURCE = "newsoul/cac_runtime_v2.md";
    private static volatile String prompt;

    static String systemPrompt() throws Exception {
        if (prompt != null) return prompt;
        try (InputStream in = AiReplyPrompt.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("课程规则未加载，请重新安装插件");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
                if (out.size() > 128 * 1024) throw new IllegalStateException("课程规则文件异常");
            }
            String loaded = new String(out.toByteArray(), StandardCharsets.UTF_8);
            if (loaded.trim().isEmpty()) throw new IllegalStateException("课程规则为空，请重新安装插件");
            prompt = loaded;
            return loaded;
        }
    }
}
