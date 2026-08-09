#import <AppKit/AppKit.h>

int main(int argc, const char *argv[]) {
    @autoreleasepool {
        if (argc != 3) {
            fprintf(stderr, "usage: generate_samantha_tts <output.aiff> <text>\n");
            return 2;
        }

        NSString *voice = nil;
        for (NSString *candidate in [NSSpeechSynthesizer availableVoices]) {
            NSDictionary *attributes = [NSSpeechSynthesizer attributesForVoice:candidate];
            if ([attributes[NSVoiceName] isEqualToString:@"Samantha"]) {
                voice = candidate;
                break;
            }
        }
        if (voice == nil) {
            fprintf(stderr, "Samantha voice is not installed\n");
            return 1;
        }

        NSSpeechSynthesizer *synthesizer = [[NSSpeechSynthesizer alloc]
                initWithVoice:voice];
        synthesizer.rate = 172.0F;
        NSURL *output = [NSURL fileURLWithPath:
                [NSString stringWithUTF8String:argv[1]]];
        NSString *text = [NSString stringWithUTF8String:argv[2]];
        if (![synthesizer startSpeakingString:text toURL:output]) {
            fprintf(stderr, "speech export could not start\n");
            return 1;
        }
        while (synthesizer.isSpeaking) {
            [[NSRunLoop currentRunLoop] runUntilDate:
                    [NSDate dateWithTimeIntervalSinceNow:0.05]];
        }

        NSDictionary *fileAttributes = [[NSFileManager defaultManager]
                attributesOfItemAtPath:output.path error:nil];
        if ([fileAttributes fileSize] <= 4096) {
            fprintf(stderr, "speech export produced no audio frames\n");
            return 1;
        }
    }
    return 0;
}
