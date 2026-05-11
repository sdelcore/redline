{
  description = "Redline — Android GitHub PR diff viewer";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            android_sdk.accept_license = true;
            allowUnfree = true;
          };
        };

        android = pkgs.androidenv.composeAndroidPackages {
          cmdLineToolsVersion = "13.0";
          platformToolsVersion = "35.0.2";
          buildToolsVersions  = [ "35.0.0" ];
          platformVersions    = [ "35" ];
          includeEmulator = false;
          includeSystemImages = false;
          includeNDK = false;
        };

        sdkRoot = "${android.androidsdk}/libexec/android-sdk";
        aapt2  = "${sdkRoot}/build-tools/35.0.0/aapt2";
      in {
        devShells.default = pkgs.mkShell {
          buildInputs = [
            android.androidsdk
            pkgs.jdk17
            pkgs.gradle
            pkgs.kotlin
          ];

          ANDROID_HOME = sdkRoot;
          ANDROID_SDK_ROOT = sdkRoot;
          JAVA_HOME = "${pkgs.jdk17.home}";

          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${aapt2}";

          shellHook = ''
            echo "redline dev shell"
            echo "  android sdk: $ANDROID_SDK_ROOT"
            echo "  jdk:         $JAVA_HOME"
          '';
        };
      });
}
