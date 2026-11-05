package com.luckyagent.android.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FileKindTest {
    @Test fun mapsExtensionAndMime() {
        assertEquals(FileVisualKind.Pdf, fileVisualKind("report.PDF", null))
        assertEquals(FileVisualKind.Pdf, fileVisualKind("blob", "application/pdf"))
        assertEquals(FileVisualKind.Archive, fileVisualKind("pack.7z", null))
        assertEquals(FileVisualKind.Archive, fileVisualKind("pack.tar.gz", null))
        assertEquals(FileVisualKind.Code, fileVisualKind("Main.kt", null))
        assertEquals(FileVisualKind.Code, fileVisualKind("app.py", null))
        assertEquals(FileVisualKind.Spreadsheet, fileVisualKind("sheet.xlsx", null))
        assertEquals(FileVisualKind.Spreadsheet, fileVisualKind("data.csv", "text/csv"))
        assertEquals(FileVisualKind.Document, fileVisualKind("notes.md", null))
        assertEquals(FileVisualKind.Document, fileVisualKind("brief.docx", null))
        assertEquals(FileVisualKind.Unknown, fileVisualKind("mystery.bin", "application/octet-stream"))
    }

    @Test fun labelsAreSpecific() {
        assertEquals("PDF", fileVisualLabel("a.pdf", null))
        assertEquals("压缩包", fileVisualLabel("a.zip", null))
        assertEquals("代码", fileVisualLabel("a.go", null))
        assertEquals("表格", fileVisualLabel("a.xls", null))
        assertEquals("文档", fileVisualLabel("a.txt", null))
        assertEquals("文件", fileVisualLabel("a.bin", null))
    }
}

class QuotePreviewMarkdownTest {
    @Test fun stripsMarkdownSyntax() {
        val preview = quotePreview("# 标题\n**粗体** 和 `code` 以及 [文档](https://example.com)")
        assertFalse(preview.contains("#"))
        assertFalse(preview.contains("*"))
        assertFalse(preview.contains("`"))
        assertFalse(preview.contains("]("))
        assertEquals("标题 粗体 和 code 以及 文档", preview)
    }

    @Test fun outboundProtocolUnchanged() {
        val block = formatQuoteBlock("assistant", "# 标题")
        assertEquals("> [引用-assistant]\n> # 标题", block)
    }
}
