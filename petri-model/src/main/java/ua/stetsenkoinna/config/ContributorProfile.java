package ua.stetsenkoinna.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** Optional, locally declared attribution. Importing a dataset never updates this profile. */
public record ContributorProfile(String id, String nickname, String organization, String group, String contacts) {
    public ContributorProfile {
        nickname = clean(nickname, 100); organization = clean(organization, 200);
        group = clean(group, 100); contacts = clean(contacts, 2000);
        id = clean(id, 100);
    }
    public ContributorProfile(String nickname, String organization, String group, String contacts) { this("", nickname, organization, group, contacts); }
    private static String clean(String text, int limit) {
        String value = text == null ? "" : text.strip();
        if (value.length() > limit) throw new IllegalArgumentException("Profile field exceeds " + limit + " characters");
        return value;
    }
    public Map<String, String> fields() {
        Map<String, String> values = new LinkedHashMap<>();
        if (!id.isEmpty()) values.put("id", id);
        if (!nickname.isEmpty()) values.put("nickname", nickname);
        if (!organization.isEmpty()) values.put("organization", organization);
        if (!group.isEmpty()) values.put("group", group);
        if (!contacts.isEmpty()) values.put("contacts", contacts);
        return values;
    }
}
