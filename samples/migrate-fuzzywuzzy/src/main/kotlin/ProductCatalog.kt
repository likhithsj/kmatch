// ---------------------------------------------------------------------------
// The app-shaped model: a product form has an identity beyond its display
// name (number + form code). Search must give back THE form that matched,
// not "some product with the same name".
// ---------------------------------------------------------------------------

data class ProductForm(
    val productNumber: String,
    val formCode: String,     // e.g. HOT vs ICED -- two forms, one display name
    val name: String,
)

// A deliberately app-like catalog: it contains the two traps a real menu has.
//  * Two forms sharing one display name ("Caffè Latte" HOT and ICED)
//  * Accented names users type without accents (Caffè, Jalapeño)
val catalog = listOf(
    ProductForm("2121", "HOT",  "Caffè Latte"),
    ProductForm("2122", "ICED", "Caffè Latte"),
    ProductForm("2123", "HOT",  "Caffè Mocha"),
    ProductForm("409",  "HOT",  "Caramel Macchiato"),
    ProductForm("405",  "HOT",  "Cappuccino"),
    ProductForm("873",  "FOOD", "Jalapeño Cheddar Bagel"),
    ProductForm("512",  "ICED", "Mango Dragonfruit Refresher"),
    ProductForm("601",  "ICED", "Cold Brew"),
)
