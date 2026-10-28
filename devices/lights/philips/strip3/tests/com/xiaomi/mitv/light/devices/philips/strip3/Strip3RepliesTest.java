package com.xiaomi.mitv.light.devices.philips.strip3;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class Strip3RepliesTest {
    private String feed(Strip3Replies replies, String text) {
        byte[] bytes=text.getBytes(StandardCharsets.US_ASCII);
        return replies.accept(bytes,bytes.length);
    }
    @Test public void errorSplitAcrossPacketsIsNotLost() {
        Strip3Replies replies=new Strip3Replies();
        assertNull(feed(replies,"ok\r\ner"));
        assertEquals("error invalid frame",feed(replies,"ror invalid frame\r\n"));
        assertEquals("error invalid frame",feed(replies,"ok\r\n"));
    }
    @Test public void normalRepliesDoNotGrowWithoutBound() {
        Strip3Replies replies=new Strip3Replies();
        for(int i=0;i<10000;i++)assertNull(feed(replies,"ok\r\n"));
        byte[] tooLong=new byte[2100];java.util.Arrays.fill(tooLong,(byte)'x');
        assertNotNull(replies.accept(tooLong,tooLong.length));
    }
    @Test public void handshakeFinishesOnItsOwnCompleteReply() {
        assertFalse(Strip3Replies.complete("get_power","ok\r\nrep_power 1\r\n"));
        assertFalse(Strip3Replies.complete("get_power","1"));
        assertTrue(Strip3Replies.complete("get_power","1\r\n"));
        assertFalse(Strip3Replies.complete("model","ok\r\n"));
        assertTrue(Strip3Replies.complete("model","philips.light.strip3\r\n"));
        assertTrue(Strip3Replies.complete("version","2.1.8_0039\r\n"));
        assertTrue(Strip3Replies.complete("set_pc_linkage 0","ok\r\n"));
        assertTrue(Strip3Replies.complete("get_power","error rejected\r\n"));
    }
    @Test public void identityComesFromValidatedCompleteReplyLines() {
        com.xiaomi.mitv.light.core.LightIdentity identity=Strip3Replies.identity("rep_power 1\r\nphilips.light.strip3\r\n","2.1.8_0039\r\n");
        assertEquals("philips.light.strip3",identity.model);assertEquals("2.1.8_0039",identity.firmware);
    }
    @Test(expected=IllegalArgumentException.class) public void bridgeMatchCannotStandInForControllerIdentity() {
        Strip3Replies.identity("other.philips.light.strip3.clone\r\n","2.1.8_0039\r\n");
    }
    @Test(expected=IllegalArgumentException.class) public void unverifiedFirmwareDoesNotAcquireAnIdentity() {
        Strip3Replies.identity("philips.light.strip3\r\n","unknown\r\n");
    }
}
