package io.github.likhithsj.menusearch

/** A trimmed, hand-written copy of the live payload's shape. */
val FIXTURE_JSON = """
{"menus":[
 {"name":"The Latest","displayOrder":1,"products":[],"children":[
   {"name":"Trending","displayOrder":1,"products":[
     {"name":"Pumpkin Spice Latte","formCode":"Hot","displayOrder":1,"productNumber":100,"productType":"Beverage","imageURL":"https://img/psl","sizes":[{"sizeCode":"Grande"}],"uri":"/product/100/hot"},
     {"name":"Caffè Mocha","formCode":"Hot","displayOrder":2,"productNumber":200,"productType":"Beverage","uri":"/product/200/hot"}
   ],"children":[]}
 ]},
 {"name":"Coffee & Espresso","displayOrder":2,"products":[],"children":[
   {"name":"Hot Coffee & Espresso","displayOrder":1,"products":[],"children":[
     {"name":"Latte","displayOrder":1,"products":[
       {"name":"Caffè Latte","formCode":"Hot","displayOrder":1,"productNumber":300,"productType":"Beverage","uri":"/product/300/hot"},
       {"name":"Pumpkin Spice Latte","formCode":"Hot","displayOrder":2,"productNumber":100,"productType":"Beverage","imageURL":"https://img/psl","sizes":[{"sizeCode":"Grande"}],"uri":"/product/100/hot"}
     ],"children":[]},
     {"name":"Mocha","displayOrder":2,"products":[
       {"name":"Caffè Mocha","formCode":"Hot","displayOrder":1,"productNumber":200,"productType":"Beverage","uri":"/product/200/hot"},
       {"name":"White Chocolate Mocha","formCode":"Hot","displayOrder":2,"productNumber":201,"productType":"Beverage","uri":"/product/201/hot"}
     ],"children":[]},
     {"name":"Cappuccino","displayOrder":3,"products":[
       {"name":"Cappuccino","formCode":"Hot","displayOrder":1,"productNumber":400,"productType":"Beverage","uri":"/product/400/hot"}
     ],"children":[]},
     {"name":"Americano","displayOrder":4,"products":[
       {"name":"Caffè Americano","formCode":"Hot","displayOrder":1,"productNumber":500,"productType":"Beverage","uri":"/product/500/hot"}
     ],"children":[]}
   ]}
 ]},
 {"name":"Frappuccino® Blended Beverage","displayOrder":3,"products":[],"children":[
   {"name":"Coffee Frappuccino®","displayOrder":1,"products":[
     {"name":"Mocha Frappuccino® Blended Beverage","formCode":"Iced","displayOrder":1,"productNumber":600,"productType":"Beverage","uri":"/product/600/iced"},
     {"name":"Caramel Frappuccino® Blended Beverage","formCode":"Iced","displayOrder":2,"productNumber":601,"productType":"Beverage","uri":"/product/601/iced"}
   ],"children":[]}
 ]},
 {"name":"Food","displayOrder":4,"products":[],"children":[
   {"name":"Bakery","displayOrder":1,"products":[],"children":[
     {"name":"Bagels","displayOrder":1,"products":[
       {"name":"Jalapeño Cheddar Bagel","formCode":"","displayOrder":1,"productNumber":700,"productType":"Food","uri":"/product/700/single"}
     ],"children":[]},
     {"name":"Croissants & Danishes","displayOrder":2,"products":[
       {"name":"Chocolate Croissant","displayOrder":1,"productNumber":701,"productType":"Food","uri":"/product/701/single"}
     ],"children":[]}
   ]}
 ]},
 {"name":"At Home Coffee","displayOrder":5,"products":[],"children":[
   {"name":"Whole Bean","displayOrder":1,"products":[],"children":[
     {"name":"Dark Roast","displayOrder":1,"products":[
       {"name":"Caffè Verona®","formCode":"Whole Bean","displayOrder":1,"productNumber":800,"productType":"Coffee","uri":"/product/800/whole-bean"}
     ],"children":[]}
   ]}
 ]}
]}
"""
