package com.streamhelper.app.service;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import org.springframework.stereotype.Service;

@Service
public class MarkdownService {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    public String render(String markdown) {
        return renderer.render(parser.parse(markdown == null ? "" : markdown));
    }
}
