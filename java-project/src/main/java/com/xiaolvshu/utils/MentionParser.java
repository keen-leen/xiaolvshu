package com.xiaolvshu.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mention文本解析工具
 * 处理[@nickname:user_id]格式的文本，提取被@的用户信息
 */
public class MentionParser {

    /**
     * 匹配HTML格式的mention链接
     * <a class="mention-link" data-user-id="user_id">@nickname</a>
     */
    private static final Pattern HTML_MENTION_PATTERN = Pattern.compile("<a[^>]*class=\"mention-link\"[^>]*data-user-id=\"([^\"]+)\"[^>]*>@([^<]+)</a>");

    /**
     * 兼容旧格式[@nickname:user_id]
     */
    private static final Pattern OLD_MENTION_PATTERN = Pattern.compile("\\[@([^:]+):([^\\]]+)\\]");

    /**
     * 提取到的用户信息
     */
    public static class MentionedUser {
        private String nickname;
        private String userId;

        public MentionedUser(String nickname, String userId) {
            this.nickname = nickname;
            this.userId = userId;
        }

        public String getNickname() {
            return nickname;
        }

        public String getUserId() {
            return userId;
        }
    }

    /**
     * 从文本中提取所有被@的用户ID
     * @param text 包含mention标记的文本
     * @return 用户信息列表
     */
    public static List<MentionedUser> extractMentionedUsers(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        List<MentionedUser> mentionedUsers = new ArrayList<>();
        Set<String> addedUserIds = new HashSet<>();

        // 匹配HTML格式
        Matcher htmlMatcher = HTML_MENTION_PATTERN.matcher(text);
        while (htmlMatcher.find()) {
            String userId = htmlMatcher.group(1);
            String nickname = htmlMatcher.group(2);
            
            if (!addedUserIds.contains(userId)) {
                mentionedUsers.add(new MentionedUser(nickname, userId));
                addedUserIds.add(userId);
            }
        }

        // 匹配旧格式
        Matcher oldMatcher = OLD_MENTION_PATTERN.matcher(text);
        while (oldMatcher.find()) {
            String nickname = oldMatcher.group(1);
            String userId = oldMatcher.group(2);

            if (!addedUserIds.contains(userId)) {
                mentionedUsers.add(new MentionedUser(nickname, userId));
                addedUserIds.add(userId);
            }
        }

        return mentionedUsers;
    }

    /**
     * 检查文本是否包含mention标记
     * @param text 要检查的文本
     * @return 是否包含mention
     */
    public static boolean hasMentions(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return HTML_MENTION_PATTERN.matcher(text).find() || OLD_MENTION_PATTERN.matcher(text).find();
    }
}
