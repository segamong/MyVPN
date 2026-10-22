# Сборка MyVPN

## APK из GitHub Actions

Откройте **Actions → MyVPN Android** в этом репозитории. Сборка запускается после
push, для pull request и вручную через **Run workflow**. После успешного выполнения
скачайте артефакт **MyVPN-debug-apks-…**, распакуйте ZIP и установите APK на Android
7.0 или новее. Для обычного современного телефона подходит `arm64-v8a`; если
архитектура неизвестна, выберите `universal`. В каталоге `playstore/debug` находится
основная версия, а в `fdroid/debug` — сборка F-Droid. Google Play для установки этих
APK не требуется.

Сборка выполняет модульные тесты обеих версий приложения, собирает все четыре
архитектуры и проверяет наличие Xray и обеих библиотек HEV внутри каждого APK.
Артефакт также содержит `SHA256SUMS.txt` и `BUILD-PROVENANCE.json` с исходными
ревизиями. Отдельный артефакт **MyVPN-reports-…** содержит результаты тестов и
диагностику Gradle, в том числе при неудачной сборке. Артефакты хранятся 30 дней.

Это установочные **debug APK**, подписанные тестовым сертификатом Android.
Секреты GitHub и пользовательский keystore не нужны. На новом CI runner ключ
создаётся заново, поэтому обновление ранее установленной CI-сборки может потребовать
удаления предыдущего приложения. Перед удалением экспортируйте профили: Android
удаляет локальные данные вместе с приложением. Успешная сборка и модульные тесты
не заменяют проверку VPN на физическом телефоне с реальным профилем сервера.

## Закреплённые исходники

Основа приложения — [v2rayNG 2.3.11](https://github.com/2dust/v2rayNG/tree/e1b73f2fa8487dfc303da52d109719c1e32386b4).
Git submodules закрепляют точные версии нативных библиотек:

| Компонент | Ревизия |
| --- | --- |
| AndroidLibXrayLite | `1f3e2c5df1c248ed42630cee2bf747643d303ecc` |
| hev-socks5-tunnel | `d9dca26c7ad0e494492244f0309e80ee583e739e` |
| golang.org/x/mobile | `v0.0.0-20260908204917-8b95e45f8d3e` |
| Loyalsoldier/geoip, база cn/private | `bf01519a9e21f366266bd48c6afae430ec8febfe` |

CI компилирует `libv2ray.aar` из gitlink AndroidLibXrayLite, а не скачивает AAR
ближайшего релиза: закреплённый commit содержит исправление утечки памяти, которого
нет в предшествующем теге. Базы `geoip.dat` и `geosite.dat` берутся из того же
закреплённого дерева исходников. Сборка не заменяет их изменяемыми файлами `latest`.
Дополнительная база `geoip-only-cn-private.dat`, необходимая для встроенных правил
маршрутизации, скачивается из закреплённого commit Loyalsoldier/geoip и проверяется
по SHA-256 из workflow.
Исходники HEV и его вложенные submodules также берутся из gitlinks.

Конкретные версии Go, JDK, Android SDK, NDK и GitHub Actions перечислены в
[рабочем процессе](../.github/workflows/myvpn-build.yml). Action-зависимости
закреплены полными SHA. Версии Kotlin, Android Gradle Plugin и библиотек приложения
читаются из [каталога версий](../V2rayNG/gradle/libs.versions.toml), Gradle — из
[wrapper](../V2rayNG/gradle/wrapper/gradle-wrapper.properties).

При обновлении нативных компонентов обновляйте submodule и ожидаемый SHA в workflow
вместе, затем повторно проверяйте все поддерживаемые ABI. Изменение toolchain или
нативных исходников требует новой сборки; ранее собранные `.aar` и `.so` в Git не
коммитятся.

## Локальная сборка

Ниже команды для Linux или WSL с установленными JDK, Android SDK, NDK и Go из
workflow. На Windows запускайте Gradle через `gradlew.bat`; нативный shell-скрипт
HEV удобнее выполнять в Linux/WSL. Сначала получите вложенные исходники:

```bash
git clone --recurse-submodules https://github.com/segamong/MyVPN.git
cd MyVPN
git submodule update --init --recursive
```

Установите SDK и оба NDK, перечисленные в workflow. Укажите `ANDROID_HOME` на
каталог SDK, `ANDROID_NDK_HOME` — на NDK для Xray, `NDK_HOME` — на NDK для HEV.
Создайте каталог для библиотек и соберите AAR:

```bash
mkdir -p V2rayNG/app/libs
cd AndroidLibXrayLite
export GOTOOLCHAIN=local
export GOFLAGS=-mod=readonly
MOBILE_VERSION=$(go list -m -f '{{.Version}}' golang.org/x/mobile)
go mod download
go mod verify
go install "golang.org/x/mobile/cmd/gomobile@$MOBILE_VERSION"
go install "golang.org/x/mobile/cmd/gobind@$MOBILE_VERSION"
export PATH="$(go env GOPATH)/bin:$PATH"
mkdir -p "$(go env GOPATH)/pkg/gomobile"
curl --fail --location --retry 3 \
  'https://raw.githubusercontent.com/Loyalsoldier/geoip/bf01519a9e21f366266bd48c6afae430ec8febfe/geoip-only-cn-private.dat' \
  -o assets/geoip-only-cn-private.dat
printf '%s  %s\n' '64021de0988e634c24410d431e0dea55c84a1abb61c457083d5b6892ed43bf05' \
  assets/geoip-only-cn-private.dat | sha256sum --check
gomobile bind -v -target=android -androidapi 24 -trimpath \
  -ldflags='-s -w -buildid= -checklinkname=0' \
  -o ../V2rayNG/app/libs/libv2ray.aar ./
cd ..
bash compile-hevtun.sh
cp -R libs/. V2rayNG/app/libs/
cd V2rayNG
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
./gradlew :app:testPlaystoreDebugUnitTest :app:testFdroidDebugUnitTest \
  :app:assemblePlaystoreDebug :app:assembleFdroidDebug
```

Готовые APK находятся в `V2rayNG/app/build/outputs/apk/`. Для разработки приложение
можно открыть в Android Studio, выбрав каталог `V2rayNG`.

В этой версии x/mobile команда `gomobile init` сама устанавливает `gobind@latest`.
Поэтому команды выше создают её рабочий каталог и устанавливают обе утилиты явно
из одной закреплённой версии; проверку SDK выполняет `gomobile bind`.

## Постоянная подпись для обновлений

Для регулярного распространения создайте собственный release keystore, храните
резервную копию и используйте один ключ для всех обновлений. Существующий
[upstream workflow](../.github/workflows/build.yml) показывает параметры
`android.injected.signing.*` и секреты `APP_KEYSTORE_BASE64`,
`APP_KEYSTORE_PASSWORD`, `APP_KEYSTORE_ALIAS`, `APP_KEY_PASSWORD`.
Его автоматический запуск ограничен upstream-веткой `master`; основной процесс
MyVPN собирает debug APK без этих секретов. Не публикуйте keystore, пароли,
пользовательские VPN-профили или приватные серверные ключи в репозитории.

Основа распространяется под GPL-3.0. При передаче APK сохраняйте сведения об
авторстве и предоставляйте получателям соответствующие исходники, включая ваши
изменения и инструкции сборки.
