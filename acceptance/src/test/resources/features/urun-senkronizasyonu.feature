# language: tr
Özellik: Backoffice'teki ürün değişiklikleri vitrine yansır
  Ürün yöneticisi ve depo görevlisi Backoffice'te çalışır; vitrin (Storefront) kısa bir gecikmeyle
  aynı duruma gelir. Adımlar iki uygulamayla da sadece HTTP üzerinden konuşur.

  Senaryo: Yeni ürün vitrine "Tükendi" olarak yansır (US1)
    Eğer ki ürün yöneticisi "Kupa" adlı ürünü 100 TRY fiyatla oluşturur
    O zaman vitrinde ürün aktif ve "Tükendi" olarak görünür

  Senaryo: Ürün bilgisi güncellemesi vitrine yansır (US2)
    Diyelim ki vitrinde "Kupa" adlı ürün 100 TRY fiyatla var
    Eğer ki ürün yöneticisi fiyatı 120 TRY yapar
    O zaman vitrinde fiyat 120 TRY olur

  Senaryo: Stok sayımı vitrine yansır (US3)
    Diyelim ki vitrinde "Kupa" adlı ürün 100 TRY fiyatla var
    Eğer ki depo görevlisi stoğu 5 olarak sayar
    Ve depo görevlisi stoğu 8 olarak sayar
    O zaman vitrinde stok 8 olur

  Senaryo: Stok sıfırlanınca ürün tükenir (US3)
    Diyelim ki vitrinde "Kupa" adlı ürün 100 TRY fiyatla var
    Ve depo görevlisi stoğu 1 olarak sayar
    Ve vitrinde stok 1 olur
    Eğer ki depo görevlisi stoğu 0 olarak sayar
    O zaman vitrinde ürün aktif ve "Tükendi" olarak görünür

  Senaryo: Satıştan kaldırılan ürün vitrinden kalkar (US4)
    Diyelim ki vitrinde "Kupa" adlı ürün 100 TRY fiyatla var
    Eğer ki ürün yöneticisi ürünü satıştan kaldırır
    O zaman ürün vitrinde listelenmez

  Senaryo: Pasif ürünün stoğu değişse de ürün vitrine dönmez (US4)
    Diyelim ki vitrinde "Kupa" adlı ürün 100 TRY fiyatla var
    Ve ürün yöneticisi ürünü satıştan kaldırır
    Ve ürün vitrinde listelenmez
    Eğer ki depo görevlisi stoğu 7 olarak sayar
    O zaman vitrinde stok 7 olur
    Ve ürün vitrinde listelenmez
