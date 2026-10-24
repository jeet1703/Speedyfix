package com.speedyfixautosales.store.web;

import com.speedyfixautosales.store.catalog.Category;
import com.speedyfixautosales.store.catalog.Feature;
import com.speedyfixautosales.store.catalog.MenuGroup;
import com.speedyfixautosales.store.catalog.ProductView;
import com.speedyfixautosales.store.catalog.Slide;
import com.speedyfixautosales.store.config.SiteProperties;
import com.speedyfixautosales.store.vehicle.Vehicle;
import java.util.List;

/** Everything the v2 homepage needs in one request. */
public record HomeResponse(
        Site site,
        List<MenuGroup> menu,
        List<Slide> slides,
        List<Feature> features,
        List<Category> popularCategories,
        List<ProductView> bestSelling,
        List<ProductView> latest,
        List<Review> reviews,
        Vehicle vehicle) {

    public record Site(String name, String tagline, String phone, String whatsapp, String email, String companyName,
                       String address, String videoUrl, String warranty, String shipsFrom) {

        static Site from(SiteProperties p) {
            return new Site(p.name(), p.tagline(), p.phone(), p.whatsapp(), p.email(), p.companyName(), p.address(),
                    p.videoUrl(), p.warranty(), p.shipsFrom());
        }
    }

    /** Verified reviews from the client's review platform. Empty until that platform is connected. */
    public record Review(String author, String location, int rating, String text) {
    }
}
