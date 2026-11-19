# ایکس‌ری کلاینت (Xray Client Android)

کلاینت بومی اندروید (Kotlin + Jetpack Compose + Material 3) با چیدمان راست‌به‌چپ فارسی و معماری ماژولار برای اتصال امن از طریق هسته **Xray-core** و سرویس بومی `VpnService`.

## وضعیت پیاده‌سازی (فاز ۱ و فاز ۲)

### پیاده‌سازی و تایید شده
- **رابط کاربری کامل فارسی (RTL)** با تم پیش‌فرض تیره سرمه‌ای/زغالی و لهجه سبز زمردی، فونت‌های محلی `Vazirmatn` و `JetBrains Mono` و چیدمان تطبیقی (`NavigationBar` / `NavigationRail`).
- **پشتیبانی از ۶ پروتکل**:
  - `VLESS` (UUID، Flow، TLS، REALITY با `pbk`/`sid`/`fp`/`sni`، WebSocket، gRPC، XHTTP، HTTPUpgrade، TCP/RAW)
  - `VMess` (Base64 JSON و URI، AlterID، Security، TLS، WS، gRPC، HTTPUpgrade، TCP/RAW)
  - `Trojan` (Password، TLS/REALITY، SNI، ALPN، WS، gRPC، TCP/RAW)
  - `Shadowsocks` (SIP002، Shadowsocks-2022، Base64 قدیمی)
  - `HTTP / HTTPS Proxy` (ساختار استاندارد Xray `http` outbound)
  - `SOCKS5 Proxy` (ساختار استاندارد Xray `socks` outbound + UDP Associate)
- **ساخت و اعتبارسنجی JSON هسته Xray**: `XrayConfigBuilder` + `ConfigValidator`.
- **ورود پیکربندی (Import)**: لینک مستقیم/کلیپ‌بورد، لیست متنی، Base64 اشتراک، فایل Xray JSON (با SAF)، و اسکنر زنده کد QR (CameraX + ZXing + انتخاب از گالری با Photo Picker).
- **مدیریت اشتراک‌ها**: افزودن، ویرایش نام، حذف، بروزرسانی تکی و گروهی با OkHttp (بدون ثبت لاگ از اطلاعات حساس)، و زمان‌بندی خودکار.
- **لایه Android VPN (`XrayVpnService` + `TunManager`)**: پیکربندی IPv4/IPv6، جلوگیری از نشت DNS، فیلتر برنامه‌ها (Per-App VPN)، محافظت از سوکت‌های بالادست با `VpnService.protect()`, و اتصال مجدد هنگام تغییر شبکه.
- **لایه انتزاع هسته بومی (`XrayEngineAdapter` + `NativeXrayBridge`)**: تشخیص خودکار باینری Xray و نمایش صریح وضعیت `ENGINE_NOT_INSTALLED` در نبود کتابخانه بومی.

### نیازمند ادغام باینری بومی (فاز ۳ و ۴)
محیط ساخت Google AI Studio کامپایل کدهای Go (`gomobile`) و Android NDK را پشتیبانی نمی‌کند. برای فعال‌سازی تونل واقعی روی دستگاه، فایل‌های کامپایل‌شده `libXray.aar` (یا `libv2ray.aar` + `libhev-socks5-tunnel.so`) را طبق راهنمای [`NATIVE_INTEGRATION.md`](NATIVE_INTEGRATION.md) به پروژه اضافه کنید.
