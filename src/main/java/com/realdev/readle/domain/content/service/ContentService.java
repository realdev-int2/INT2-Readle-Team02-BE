package com.realdev.readle.domain.content.service;

import com.realdev.readle.domain.content.dto.request.ContentExtractRequest;
import com.realdev.readle.domain.content.dto.response.ContentExtractResponse;
import com.realdev.readle.global.util.crawler.WebCrawler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContentService {

  private final WebCrawler webCrawler;

  public ContentExtractResponse extract(ContentExtractRequest request) {
    WebCrawler.CrawledDocument crawledDocument = webCrawler.crawl(request.url());
    return new ContentExtractResponse(crawledDocument.title(), crawledDocument.content());
  }
}
