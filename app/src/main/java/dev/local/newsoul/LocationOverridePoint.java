package dev.local.newsoul;

import org.json.JSONObject;

/** A selected host POI in Soul's BD-09 coordinate system; never mixes in real-location fields. */
final class LocationOverridePoint {
    static final String ENABLED = "location_override_enabled_v1";
    static final String POINT = "location_override_point_v1";
    final double latitude, longitude;
    final String title, city, province, country, adCode, cityCode;

    LocationOverridePoint(double latitude, double longitude, String title, String city,
            String province, String country, String adCode, String cityCode) {
        // Soul treats zero and Double.MIN_VALUE as missing coordinates.
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180
                || latitude == 0 || longitude == 0 || latitude == Double.MIN_VALUE || longitude == Double.MIN_VALUE)
            throw new IllegalArgumentException("Invalid coordinates");
        this.latitude = latitude; this.longitude = longitude;
        this.title = text(title); this.city = text(city); this.province = text(province);
        this.country = text(country); this.adCode = text(adCode); this.cityCode = text(cityCode);
        if (this.title.isEmpty() || this.city.isEmpty()) throw new IllegalArgumentException("Missing place");
    }
    private static String text(String text) {
        String value = text == null ? "" : text.trim();
        if (value.length() > 512 || value.indexOf('\u0000') >= 0) throw new IllegalArgumentException("Invalid place");
        return value;
    }
    int locationWhere() {
        if (country.isEmpty()) return 2;
        return country.equals("中国") || country.equals("中华人民共和国") || country.equalsIgnoreCase("China")
                || country.equalsIgnoreCase("CN") ? 1 : 0;
    }
    String encode() throws Exception {
        return new JSONObject().put("schema", 1).put("coordinateType", "bd09ll")
                .put("latitude", latitude).put("longitude", longitude).put("title", title).put("city", city)
                .put("province", province).put("country", country).put("adCode", adCode).put("cityCode", cityCode).toString();
    }
    static LocationOverridePoint decode(String value) throws Exception {
        if (value == null || value.length() > 8192) throw new IllegalArgumentException("Invalid place");
        JSONObject json = new JSONObject(value);
        if (json.getInt("schema") != 1 || !json.getString("coordinateType").equals("bd09ll"))
            throw new IllegalArgumentException("Invalid coordinate type");
        return new LocationOverridePoint(json.getDouble("latitude"), json.getDouble("longitude"),
                json.getString("title"), json.getString("city"), json.getString("province"),
                json.getString("country"), json.getString("adCode"), json.getString("cityCode"));
    }
}
