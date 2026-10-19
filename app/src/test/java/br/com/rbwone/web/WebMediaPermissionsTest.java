package br.com.rbwone.web;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class WebMediaPermissionsTest {
    static final String AUDIO="android.webkit.resource.AUDIO_CAPTURE", VIDEO="android.webkit.resource.VIDEO_CAPTURE";
    static final String MIC="android.permission.RECORD_AUDIO", CAMERA="android.permission.CAMERA";
    @Test public void grantedMicrophoneNeverRequestsAndroidAgain() {
        assertTrue(WebMediaPermissions.missing(new String[]{AUDIO}, MIC::equals).isEmpty());
    }
    @Test public void mixedRequestAsksOnlyForMissingCamera() {
        assertEquals(List.of(CAMERA),WebMediaPermissions.missing(new String[]{AUDIO,VIDEO},MIC::equals));
    }
    @Test public void rechecksRevocationInsteadOfRememberingStalePermission() {
        Set<String> permissions=new HashSet<>(List.of(MIC));
        assertTrue(WebMediaPermissions.missing(new String[]{AUDIO},permissions::contains).isEmpty());
        permissions.clear();
        assertEquals(List.of(MIC),WebMediaPermissions.missing(new String[]{AUDIO},permissions::contains));
    }
    @Test public void unknownAndEmptyResourcesAreNeverGranted() {
        for(String[] resources:new String[][]{new String[]{},new String[]{AUDIO,"future.permission"}}) {
            assertThrows(IllegalArgumentException.class,()->WebMediaPermissions.missing(resources,p->true));
        }
    }
    @Test public void deduplicatesNativePermissionDialog() {
        assertEquals(List.of(MIC),WebMediaPermissions.missing(new String[]{AUDIO,AUDIO},p->false));
    }
}
