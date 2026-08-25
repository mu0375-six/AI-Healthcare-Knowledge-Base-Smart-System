package com.healthkb.rag;

import cn.hutool.http.HtmlUtil;

import java.util.List;

public final class OfficialHtmlCleaner {

    private static final List<String> CUT_MARKERS = List.of(
            "相关链接", "实况报道", "其它链接", "其他链接", "网站地图", "版权所有");

    private OfficialHtmlCleaner() {
    }

    public static String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String s = html;
        s = s.replaceAll("(?is)<script[^>]*>.*?</script>", " ");
        s = s.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
        s = s.replaceAll("(?is)<noscript[^>]*>.*?</noscript>", " ");
        s = s.replaceAll("(?is)<nav[^>]*>.*?</nav>", " ");
        s = s.replaceAll("(?is)<footer[^>]*>.*?</footer>", " ");
        s = HtmlUtil.cleanHtmlTag(s);
        s = HtmlUtil.unescape(s);
        s = s.replace('\u00a0', ' ');
        s = s.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        s = s.replaceAll(" ?\\n ?", "\n");
        s = s.replaceAll("\\n{3,}", "\n\n");
        for (String marker : CUT_MARKERS) {
            int i = s.indexOf(marker);
            if (i > 500) {
                s = s.substring(0, i);
                break;
            }
        }
        return s.trim();
    }
}
