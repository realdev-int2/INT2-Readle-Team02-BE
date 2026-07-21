package com.realdev.readle.domain.quiz.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record ResultReportHistoryRequest(
    @Schema(description = "다음 페이지 조회 커서. 첫 조회에서는 생략합니다.") String cursor,
    @Schema(description = "조회 개수", defaultValue = "10", minimum = "1", maximum = "50")
        @Positive(message = "size는 양수여야 합니다.") @Max(value = 50, message = "size는 50 이하여야 합니다.") Integer size,
    @Schema(
            description = "정렬 방식",
            defaultValue = "latest",
            allowableValues = {"latest", "oldest"})
        @Pattern(regexp = "latest|oldest", message = "sort는 latest 또는 oldest만 허용됩니다.") String sort,
    @Schema(description = "태그 ID 필터", minimum = "1") @Positive(message = "tagId는 양수여야 합니다.") Long tagId) {

  public ResultReportHistoryRequest {
    size = size == null ? 10 : size;
    sort = sort == null ? "latest" : sort;
  }
}
