# kotbridge

kotbridge is a proof of concept Kotlin compiler plugin and Gradle build configuration
to enable extracting and compiling Kotlin source code fragments for usage in JavaScript environments.

Usage examples:
- [Gradle configuration enabling compilation of K/JVM to K/js](/testing/build.gradle.kts#L29-L212)
- [Running Kotlin code in Selenium executeScript method](/testing/src/test/kotlin/SeleniumDemoTests.kt#L73-L85)
- [Running Kotlin code in Playwright evaluate* methods](/testing/src/test/kotlin/PlaywrightDemoTests.kt#L77-L95)
```kotlin
// Run kotlin code in Selenium executeScript
// executeKtJs is defined in https://github.com/TarCV/kotbridge/blob/bridge-main/testing/src/test/kotlin/SeleniumDemoTests.kt#L45-L67
chromeDriver.executeKtJs { _ -> // code inside this block is executed in the JS world //
    kotlinx.browser.document.querySelectorAll(".column-15")                           //
        .asList()                                                                     //
        .map { it.textContent }                                                       //
        .toTypedArray()                                                               //
}                               // code inside this block is executed in the JS world //
```

kotbridge is based on [kapshot](https://github.com/mfwgenerics/kapshot).
If you star kotbridge, please, don't forget to star kapshot too.

---
All trademarks are the property of their respective owners. All company, product and service names used in this description are for identification purposes only. Use of these names or brands does not imply endorsement.
