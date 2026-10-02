# language: tr
Özellik: Hatalar ve kesintiler sistemi kilitlemez
  Mesajlar kaybolmaz; işlenemeyen mesajlar diğerlerini bekletmez.

  Senaryo: Vitrin kapalıyken yapılan değişiklikler kaybolmaz (US5-1)
    Diyelim ki vitrinde "Kupa" adlı ürün 100 TRY fiyatla var
    Ve vitrin uygulaması kapalı
    Eğer ki ürün yöneticisi fiyatı 120 TRY yapar
    Ve ürün yöneticisi açıklamayı "Büyük boy" yapar
    Ve depo görevlisi stoğu 7 olarak sayar
    Ve değişiklikler kuyruklarda birikir
    Ve vitrin uygulaması tekrar açılır
    O zaman vitrinde fiyat 120 TRY olur
    Ve vitrinde açıklama "Büyük boy" olur
    Ve vitrinde stok 7 olur

  Senaryo: Ürünü vitrine hiç ulaşmamış stok mesajı park edilir (US5-2)
    Eğer ki vitrine hiç ulaşmamış bir ürün için stok mesajı gelir
    O zaman mesaj 3 denemeden sonra park kuyruğuna düşer

  Senaryo: Bozuk mesaj tekrar denenmez, diğerlerini bekletmez (US5-3)
    Diyelim ki vitrinde "Kupa" adlı ürün 100 TRY fiyatla var
    Eğer ki ürün için stoğu -5 olan bozuk bir mesaj gelir
    Ve depo görevlisi stoğu 3 olarak sayar
    O zaman bozuk mesaj tekrar denenmeden park kuyruğuna düşer
    Ve vitrinde stok 3 olur
