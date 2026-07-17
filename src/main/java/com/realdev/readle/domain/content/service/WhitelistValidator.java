package com.realdev.readle.domain.content.service;

import com.realdev.readle.domain.content.config.ContentValidationProperties;
import com.realdev.readle.domain.content.entity.Content;
import com.realdev.readle.domain.content.entity.CrawlStatus;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WhitelistValidator {

  private final ContentValidationProperties properties;

  public boolean isEligibleForWhitelist(Content content) {
    if (content.getCrawlStatus() != CrawlStatus.SUCCESS) {
      return false;
    }

    String url = content.getOriginalUrl();
    if (url == null || url.isBlank()) {
      return false;
    }

    try {
      URI parsedUri = new URI(url);
      String host = parsedUri.getHost();
      if (host == null || host.isBlank()) {
        return false;
      }

      return properties.whitelistDomains().stream()
          .anyMatch(
              domain ->
                  host.equalsIgnoreCase(domain)
                      || host.toLowerCase().endsWith("." + domain.toLowerCase()));
    } catch (Exception e) {
      return false;
    }
  }
}
