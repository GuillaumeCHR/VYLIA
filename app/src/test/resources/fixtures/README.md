# Fixtures de test

`sample.dex` : DEX réel (1,3 Ko) produit par `javac --release 11` puis `d8 --min-api 26` (build-tools 36.0.0)
à partir de trois classes : `com.adobe.mobile.Analytics` (avec la classe interne `Analytics$Inner`),
`com.facebook.ads.AdView` et `app.example.MainActivity` (qui référence `AdView`).
