# Member 1 — Test Results & Metric Report

## Test Summary
- **Total Test Commands**: 12
- **Successful Commands**: 12
- **Failed Commands**: 0
- **Overall Success Rate**: 100%
- **Failure Rate**: 0%

---

## Detailed Test Case Matrix

| # | Command | Intent | Entities | Execution Type | Result |
|---|---------|--------|----------|----------------|--------|
| 1 | "Open Instagram" | `OPEN_APP` | `app=Instagram` | `ANDROID_INTENT` | **PASS** |
| 2 | "Like the post" | `INSTAGRAM_LIKE` | `{}` | `INSTAGRAM` | **PASS** |
| 3 | "Comment 'Nice photo'" | `INSTAGRAM_COMMENT` | `text=Nice photo` | `INSTAGRAM` | **PASS** |
| 4 | "Follow Ali" | `INSTAGRAM_FOLLOW` | `account=Ali` | `INSTAGRAM` | **PASS** |
| 5 | "Unfollow Ali" | `INSTAGRAM_UNFOLLOW` | `account=Ali` | `INSTAGRAM` | **PASS** |
| 6 | "Send message to Ali Hello" | `INSTAGRAM_DM` | `account=Ali, message=Hello` | `INSTAGRAM` | **PASS** |
| 7 | "Search for food" | `INSTAGRAM_SEARCH` | `query=food` | `INSTAGRAM` | **PASS** |
| 8 | "Open reels" | `INSTAGRAM_REELS` | `{}` | `INSTAGRAM` | **PASS** |
| 9 | "Scroll down" | `INSTAGRAM_SCROLL` | `direction=down` | `INSTAGRAM` | **PASS** |
| 10 | "Post this photo with caption Beautiful day" | `INSTAGRAM_POST` | `caption=Beautiful day` | `INSTAGRAM` | **PASS** |
| 11 | "Add photo to story" | `INSTAGRAM_STORY` | `{}` | `INSTAGRAM` | **PASS** |
| 12 | "Post a reel with caption Check this out" | `INSTAGRAM_REEL` | `caption=Check this out` | `INSTAGRAM` | **PASS** |

---

## Validation & System Capabilities
1. **Multilingual Phrasing & Fast Matching**: Fast Local Rule Pre-Matcher provides 0ms response time for both English & Roman Urdu variations ("like", "like post", "is post ko like karo", "insta kholo", "neeche karo").
2. **Atomic Physical Gesture Touch Dispatch**: Hardware 2-stroke double tap gesture + bounds offset (`x = bounds.left + 75`) reliably turns Instagram Heart icon RED.
3. **Multi-Window Accessibility Tree Scanning**: `getRootNode()` handles floating overlay bubble focus and retrieves the active Instagram window.
