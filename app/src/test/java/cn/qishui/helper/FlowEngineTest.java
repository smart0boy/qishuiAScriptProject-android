package cn.qishui.helper;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class FlowEngineTest {
    FlowEngine.Settings settings = new FlowEngine.Settings(3, 15, false);
    static FlowEngine.Token t(String text, double x, double y) { return new FlowEngine.Token(text, x - 30, y - 15, x + 30, y + 15); }
    static FlowEngine.Screen s(FlowEngine.Token... tokens) { return new FlowEngine.Screen(1080, 2376, FlowEngine.TARGET, Arrays.asList(tokens)); }
    static FlowEngine.Token counter() { return t("20秒后可领取奖励", 800, 165); }
    static FlowEngine.Screen room() { return s(t("关注", 421, 198), t("更多直播", 910, 308), t("说点什么", 207, 2163), t("关闭", 1014, 198)); }
    static FlowEngine.Screen banner() { return s(t("2s后进入直播间", 471, 1799), t("取消", 709, 1799)); }
    static FlowEngine.Screen scale(FlowEngine.Screen input, int width, int height) {
        List<FlowEngine.Token> output = new ArrayList<>();
        for (FlowEngine.Token t : input.tokens) output.add(new FlowEngine.Token(t.text, t.left * width / input.width, t.top * height / input.height, t.right * width / input.width, t.bottom * height / input.height));
        return new FlowEngine.Screen(width, height, input.packageName, output);
    }
    @Test public void waitsForRealCountdown() { assertEquals("wait", new FlowEngine(settings).tick(s(counter()), 1).kind); }
    @Test public void noAdCloseBeforeCompletion() { assertEquals("wait", new FlowEngine(settings).tick(s(counter(), t("×", 980, 165)), 1).kind); }
    @Test public void completedAdUsesCloseIcon() { assertEquals("×", new FlowEngine(settings).tick(s(t("领取成功", 800, 165), t("×", 980, 165)), 1).target.text); }
    @Test public void rewardClickRequiresSubsequentTransition() {
        FlowEngine e = new FlowEngine(settings);
        FlowEngine.Decision d = e.tick(s(t("领取成功", 800, 165)), 1); e.actionResult(d, true, 1);
        d = e.tick(s(t("领取奖励", 540, 1269)), 3); e.actionResult(d, true, 3);
        assertEquals(0, e.rounds);
        assertEquals("wait", e.tick(s(t("领取奖励", 540, 1269)), 5).kind);
        e.tick(s(counter()), 7); assertEquals(1, e.rounds);
        e.tick(s(counter()), 9); assertEquals(1, e.rounds);
    }
    @Test public void oneRoundLimitStopsOnlyAfterTransition() {
        FlowEngine e = new FlowEngine(new FlowEngine.Settings(1, 15, false));
        FlowEngine.Decision d = e.tick(s(t("领取成功", 800, 165)), 1); e.actionResult(d, true, 1);
        d = e.tick(s(t("领取奖励", 540, 1269)), 3); e.actionResult(d, true, 3);
        assertEquals("stop", e.tick(s(counter()), 5).kind); assertEquals(1, e.rounds);
    }
    @Test public void foreignAppPauses() { assertEquals("pause", new FlowEngine(settings).tick(new FlowEngine.Screen(1080, 2376, "other.app", room().tokens), 1).kind); }
    @Test public void lowerShortBannerIsCancelled() { assertEquals("cancel_live", new FlowEngine(settings).tick(banner(), 1).key); }
    @Test public void mergedBannerFindsRightHandCancel() {
        FlowEngine.Token result = FlowEngine.findLiveCancel(s(new FlowEngine.Token("3s后进入直播间 | 取消", 225, 1605, 855, 1690)));
        assertNotNull(result); assertTrue(result.x > 720); assertTrue(result.x < 855);
    }
    @Test public void unrelatedCancelIsIgnored() { assertNull(FlowEngine.findLiveCancel(s(t("确认取消下载", 400, 1600), t("取消", 700, 1600)))); }
    @Test public void cancelDoesNotChangeRoundOrPhase() {
        FlowEngine e = new FlowEngine(settings); e.tick(s(counter()), 1);
        FlowEngine.Decision d = e.tick(banner(), 3); e.actionResult(d, true, 3);
        assertEquals("WATCHING", e.phase); assertEquals(0, e.rounds);
        assertEquals("stop", e.tick(s(counter()), 153).kind);
    }
    @Test public void nativeRoomCloseIsDetected() { assertEquals("退出直播间", FlowEngine.findLiveExit(room()).text); }
    @Test public void adWithLiveWordsIsNotARoom() { assertNull(FlowEngine.findLiveExit(s(counter(), t("更多直播", 910, 308), t("关注", 421, 198), t("×", 1014, 198)))); }
    @Test public void oneRoomMarkerCannotTriggerClose() { assertNull(FlowEngine.findLiveExit(s(t("关注", 421, 198), t("关闭", 1014, 198)))); }
    @Test public void exitReturnsToOriginalAdPhase() {
        FlowEngine e = new FlowEngine(settings); e.tick(s(counter()), 1);
        FlowEngine.Decision d = e.tick(room(), 3); e.actionResult(d, true, 3);
        e.tick(s(counter()), 6); assertEquals("WATCHING", e.phase); assertEquals(0, e.rounds);
    }
    @Test public void completedAdAfterRoomRecoversVerify() {
        FlowEngine e = new FlowEngine(settings);
        FlowEngine.Decision d = e.tick(s(t("领取成功", 800, 165)), 1); e.actionResult(d, true, 1);
        d = e.tick(s(t("领取奖励", 540, 1269)), 3); e.actionResult(d, true, 3);
        d = e.tick(room(), 5); e.actionResult(d, true, 5);
        e.tick(s(t("领取成功", 800, 165)), 8); assertEquals(1, e.rounds);
    }
    @Test public void roomCloseAttemptsAreBounded() {
        FlowEngine e = new FlowEngine(settings);
        for (int now : new int[]{1, 3, 5}) { FlowEngine.Decision d = e.tick(room(), now); e.actionResult(d, true, now); }
        assertEquals("stop", e.tick(room(), 7).kind);
    }
    @Test public void threeFailedGesturesStopWithoutCounting() {
        FlowEngine e = new FlowEngine(settings);
        for (int now : new int[]{1, 3, 5}) { FlowEngine.Decision d = e.tick(s(t("领取成功", 800, 165)), now); e.actionResult(d, false, now); }
        assertEquals("STOPPED", e.phase); assertEquals(0, e.rounds);
    }
    @Test public void commonResolutionsPreserveCancelAndExitLocation() {
        for (int[] size : new int[][]{{720, 1600}, {1080, 2376}, {1440, 3200}, {1200, 1920}}) {
            FlowEngine.Token close = FlowEngine.findLiveExit(scale(room(), size[0], size[1]));
            FlowEngine.Token cancel = FlowEngine.findLiveCancel(scale(banner(), size[0], size[1]));
            assertNotNull(Arrays.toString(size), close); assertNotNull(Arrays.toString(size), cancel);
            assertEquals(1014 / 1080.0, close.x / size[0], .00001);
            assertEquals(1799 / 2376.0, cancel.y / size[1], .00001);
        }
    }
    @Test public void ambiguousRoomCloseIsNotClicked() {
        List<FlowEngine.Token> tokens = new ArrayList<>(room().tokens); tokens.add(t("关闭", 920, 150));
        assertNull(FlowEngine.findLiveExit(new FlowEngine.Screen(1080, 2376, FlowEngine.TARGET, tokens)));
    }
    @Test public void quotaMessageStops() { assertEquals("stop", new FlowEngine(settings).tick(s(t("今日次数已用完", 540, 1000)), 1).kind); }
    @Test public void timeLimitStops() { assertEquals("stop", new FlowEngine(settings).tick(s(counter()), 901).kind); }
    @Test public void observedAccessibleCompletionContinuesRewardFlow() {
        FlowEngine e = new FlowEngine(settings);
        String observed = FlowEngine.accessibilityLabel("领取成功,关闭,按钮");
        FlowEngine.Decision d = e.tick(s(new FlowEngine.Token(observed, 776, 120, 1032, 216)), 1);
        assertEquals("complete", d.key); assertEquals(904, d.target.x, 0);
        e.actionResult(d, true, 1);
        d = e.tick(s(t("领取奖励", 540, 1269)), 3); assertEquals("reward", d.key);
        e.actionResult(d, true, 3); e.tick(s(counter()), 5); assertEquals(1, e.rounds);
    }
    @Test public void accessibleRoomAndRewardRolesAreRecognized() {
        assertEquals("关闭", FlowEngine.accessibilityLabel("关闭，按钮"));
        assertEquals("更多直播", FlowEngine.accessibilityLabel("更多直播,按钮"));
        assertEquals("领取奖励", FlowEngine.accessibilityLabel("领取奖励,按钮"));
        assertEquals("说点什么", FlowEngine.accessibilityLabel("说点什么,输入框"));
    }
    @Test public void unrelatedAccessibleTextDoesNotBecomeCompletion() {
        String unrelated = "领取成功,推荐下载";
        assertEquals(unrelated, FlowEngine.accessibilityLabel(unrelated));
        assertEquals("wait", new FlowEngine(settings).tick(s(t(FlowEngine.accessibilityLabel(unrelated), 904, 168)), 1).kind);
        assertEquals("×,按钮", FlowEngine.accessibilityLabel("×,按钮"));
    }
    @Test(expected=IllegalArgumentException.class) public void invalidSettingsAreRejected() { new FlowEngine.Settings(51, 15, false); }
}
