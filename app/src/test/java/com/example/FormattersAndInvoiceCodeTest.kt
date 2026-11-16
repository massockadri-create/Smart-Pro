package com.example

import com.example.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersAndInvoiceCodeTest {

    @Test
    fun categoryCodePrefix_stripsAccentsAndUppercases() {
        assertEquals("BOI", Formatters.categoryCodePrefix("Boissons"))
        assertEquals("ELE", Formatters.categoryCodePrefix("Électronique"))
        assertEquals("ALI", Formatters.categoryCodePrefix("Aliments"))
        assertEquals("PRO", Formatters.categoryCodePrefix("Produits ménagers"))
        assertEquals("DIV", Formatters.categoryCodePrefix("   "))
    }

    @Test
    fun productInvoiceCode_appendsCategoryPrefixToInvoiceNumber() {
        assertEquals("FAC-SSP-1007-BOI", Formatters.productInvoiceCode("FAC-SSP-1007", "Boissons"))
        assertEquals("FAC-SSP-1007-ALI", Formatters.productInvoiceCode("FAC-SSP-1007", "Aliments"))
        assertEquals("PRO-SSP-1008-ACC", Formatters.productInvoiceCode("PRO-SSP-1008", "Accessoires"))
    }

    @Test
    fun autoProductReference_buildsInvoicePrefixSequenceAndCategoryStart() {
        assertEquals("FAC-SSP-1007-BOI", Formatters.autoProductReference("FAC-SSP-1007", "Boissons"))
        assertEquals("FAC-SSP-1007-ELE", Formatters.autoProductReference("FAC-ssp-1007", "Électronique"))
    }
}
