package com.rabbitlab.storefront.application;

import com.rabbitlab.storefront.application.port.in.BrowseCatalogUseCase;
import com.rabbitlab.storefront.application.port.in.CatalogUpdate;
import com.rabbitlab.storefront.application.port.in.UpdateCatalogUseCase;
import com.rabbitlab.storefront.application.port.out.Transaction;
import com.rabbitlab.storefront.domain.CatalogItem;
import com.rabbitlab.storefront.domain.Sku;
import com.rabbitlab.storefront.domainservice.CatalogRepository;

import java.util.List;
import java.util.Optional;

/**
 * Katalog use case'leri. Güncelleme kalıbı: kilitleyerek yükle → domain'e sor → değiştiyse kaydet.
 *
 * <p>Neden kilit (pessimistic)? Backoffice'te optimistic locking kullandık: çakışma nadir, çakışınca
 * kullanıcıya "tekrar dene" denir. Burada ise katalog ve stok consumer'ları AYNI satıra sürekli yazıyor;
 * çakışma olağan. Hata verip mesajı tekrar kuyruğa atmak yerine kısa süre sırada beklemek daha ucuz.
 */
public final class CatalogService implements UpdateCatalogUseCase, BrowseCatalogUseCase {

    private final CatalogRepository repository;
    private final Transaction transaction;

    public CatalogService(CatalogRepository repository, Transaction transaction) {
        this.repository = repository;
        this.transaction = transaction;
    }

    @Override
    public void apply(CatalogUpdate update) {
        transaction.execute(() -> {
            switch (update) {
                case CatalogUpdate.NewProduct p -> repository.insertIfAbsent(
                        CatalogItem.register(p.sku(), p.name(), p.description(), p.price(), p.version()));
                case CatalogUpdate.DetailsChanged d -> change(d.sku(),
                        item -> item.changeDetails(d.name(), d.description(), d.price(), d.version()));
                case CatalogUpdate.StockChanged s -> change(s.sku(), item -> item.changeStock(s.quantity(), s.version()));
                case CatalogUpdate.Withdrawn w -> change(w.sku(), item -> item.withdraw(w.version()));
            }
        });
    }

    private void change(Sku sku, Change change) {
        CatalogItem item = repository.getForUpdate(sku);
        if (change.applyTo(item)) {
            repository.update(item);
        }
    }

    @Override
    public Optional<CatalogItem> find(Sku sku) {
        return repository.find(sku);
    }

    @Override
    public List<CatalogItem> visibleItems() {
        return repository.findVisible();
    }

    @FunctionalInterface
    private interface Change {
        boolean applyTo(CatalogItem item);
    }
}
