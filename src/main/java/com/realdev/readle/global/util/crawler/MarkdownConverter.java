package com.realdev.readle.global.util.crawler;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

public class MarkdownConverter {

  public static String convert(Element element) {
    StringBuilder markdown = new StringBuilder();

    element.traverse(
        new NodeVisitor() {
          @Override
          public void head(Node node, int depth) {
            if (node instanceof Element el) {
              String tagName = el.tagName();
              switch (tagName) {
                case "h1" -> markdown.append("\n\n# ");
                case "h2" -> markdown.append("\n\n## ");
                case "h3" -> markdown.append("\n\n### ");
                case "h4", "h5", "h6" -> markdown.append("\n\n#### ");
                case "p", "div" -> {
                  if (!markdown.isEmpty() && markdown.charAt(markdown.length() - 1) != '\n') {
                    markdown.append("\n\n");
                  }
                }
                case "li" -> markdown.append("\n- ");
                case "br" -> markdown.append("\n");
              }
            } else if (node instanceof TextNode textNode) {
              String text = textNode.text();
              if (!text.isEmpty()) {
                markdown.append(text);
              }
            }
          }

          @Override
          public void tail(Node node, int depth) {
            if (node instanceof Element el) {
              String tagName = el.tagName();
              if (tagName.equals("p") || tagName.equals("div")) {
                markdown.append("\n\n");
              }
            }
          }
        });

    // 연속된 공백 및 줄바꿈 정리 (노이즈 정규화)
    return markdown
        .toString()
        .replaceAll("(?m)^[ \t]*\r?\n", "") // 빈 줄 제거
        .replaceAll("(\r?\n){3,}", "\n\n") // 3개 이상 줄바꿈은 2개로 압축
        .trim();
  }
}
