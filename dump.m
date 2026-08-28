#import <Foundation/Foundation.h>
#import <PDFKit/PDFKit.h>
#include <stdio.h>

int main(int argc, const char * argv[]) {
    @autoreleasepool {
        NSURL *url = [NSURL fileURLWithPath:@"project.pdf"];
        PDFDocument *doc = [[PDFDocument alloc] initWithURL:url];
        if (!doc) return 1;
        for (NSUInteger i = 0; i < [doc pageCount]; i++) {
            PDFPage *page = [doc pageAtIndex:i];
            printf("
--- PAGE %lu ---
", (unsigned long)(i + 1));
            printf("%s
", [[page string] UTF8String]);
        }
    }
    return 0;
}
