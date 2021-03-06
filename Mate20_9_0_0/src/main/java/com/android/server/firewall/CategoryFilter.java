package com.android.server.firewall;

import android.content.ComponentName;
import android.content.Intent;
import java.io.IOException;
import java.util.Set;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

class CategoryFilter implements Filter {
    private static final String ATTR_NAME = "name";
    public static final FilterFactory FACTORY = new FilterFactory("category") {
        public Filter newFilter(XmlPullParser parser) throws IOException, XmlPullParserException {
            String categoryName = parser.getAttributeValue(null, "name");
            if (categoryName != null) {
                return new CategoryFilter(categoryName, null);
            }
            throw new XmlPullParserException("Category name must be specified.", parser, null);
        }
    };
    private final String mCategoryName;

    /* synthetic */ CategoryFilter(String x0, AnonymousClass1 x1) {
        this(x0);
    }

    private CategoryFilter(String categoryName) {
        this.mCategoryName = categoryName;
    }

    public boolean matches(IntentFirewall ifw, ComponentName resolvedComponent, Intent intent, int callerUid, int callerPid, String resolvedType, int receivingUid) {
        Set<String> categories = intent.getCategories();
        if (categories == null) {
            return false;
        }
        return categories.contains(this.mCategoryName);
    }
}
