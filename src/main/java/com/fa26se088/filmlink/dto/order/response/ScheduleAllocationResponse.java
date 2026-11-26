package com.fa26se088.filmlink.dto.order.response;

import java.time.LocalDate;

/** BR-59: số cuộn của một nhóm (quy trình, khổ phim) được xếp vào một ngày làm việc (giờ VN). */
public record ScheduleAllocationResponse(
    LocalDate workDate, Long filmProcessId, Long filmFormatId, int rollCount) {}
