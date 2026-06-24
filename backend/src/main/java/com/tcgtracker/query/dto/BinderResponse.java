package com.tcgtracker.query.dto;

import java.util.List;

/** All filled binder pockets plus the number of pages to render (always >= 1). */
public record BinderResponse(
    List<BinderSlotDto> slots,
    int pageCount
) {}
