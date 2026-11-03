package com.frozendawn.client.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.AudioFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlindHurtTextureTest {
    private ByteBuffer tone(double frequency) {
        var data=ByteBuffer.allocate(48000*2).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<48000;i++)data.putShort((short)(8000*Math.sin(2*Math.PI*frequency*i/48000)));
        return data.flip();
    }
    private double rms(ByteBuffer pcm) {
        var b=pcm.duplicate().order(ByteOrder.LITTLE_ENDIAN);double sum=0;int count=0;
        while(b.hasRemaining()){double x=b.getShort();if(count++>4800)sum+=x*x;}return Math.sqrt(sum/(count-4801));
    }
    @Test void voiceIsRaisedAndHighHissIsAttenuatedWithoutAddingAnotherPerformer() {
        var format=new AudioFormat(48000,16,1,true,false);
        var voice=BlindHurtTexture.process(tone(1000),format,1.1);
        assertEquals((int)Math.ceil(48000/1.1)*2,voice.remaining());
        var b=voice.duplicate().order(ByteOrder.LITTLE_ENDIAN);int rising=0;short last=0;
        while(b.hasRemaining()){short x=b.getShort();if(last<=0&&x>0)rising++;last=x;}
        assertTrue(rising>=995&&rising<=1005); // Same cycles, shorter performance: approximately1100Hz.
        var hiss=BlindHurtTexture.process(tone(18000),format,1.1);
        assertTrue(rms(hiss)<rms(voice)*.08);
    }
    @Test void sourceAndSharedVoiceStateArePreservedAcrossCalls() {
        var input=tone(500);byte[] before=input.array().clone();var format=new AudioFormat(48000,16,1,true,false);
        var first=BlindHurtTexture.process(input.asReadOnlyBuffer(),format,.66);
        var second=BlindHurtTexture.process(input.asReadOnlyBuffer(),format,.66);
        assertEquals(0,input.position());assertArrayEquals(before,input.array());assertEquals(first,second);
    }
    @Test void malformedOrOversizedResourceAudioIsBounded() {
        assertThrows(IllegalArgumentException.class,()->BlindHurtTexture.process(ByteBuffer.allocate(0),new AudioFormat(48000,8,1,true,false),1.1));
        assertThrows(IllegalArgumentException.class,()->BlindHurtTexture.process(ByteBuffer.allocate(4_000_002),new AudioFormat(48000,16,1,true,false),1.1));
        assertThrows(IllegalArgumentException.class,()->BlindHurtTexture.process(ByteBuffer.allocate(4),new AudioFormat(1,16,1,true,false),1.1));
        assertThrows(IllegalArgumentException.class,()->BlindHurtTexture.process(ByteBuffer.allocate(3),new AudioFormat(48000,16,1,true,false),1.1));
        assertEquals(0,BlindHurtTexture.process(ByteBuffer.allocate(0),new AudioFormat(48000,16,1,true,false),1.1).remaining());
    }
}
