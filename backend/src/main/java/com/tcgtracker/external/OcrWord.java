package com.tcgtracker.external;

/**
 * One OCR-detected word and its bounding box (pixels), derived from a Vision
 * {@code textAnnotations} entry. {@code height}/{@code top}/{@code left} let the
 * parser pick the card's title (largest text near the top).
 */
public record OcrWord(String text, double height, double top, double left) {}
