#!/usr/bin/env bash
# Bumps the versions Dependabot can't see (plain Maven properties, a curl download in the Dockerfile and the Maven
# wrapper) to their latest releases. Run from the repository root; it only edits files, committing and opening the
# pull request is left to .github/workflows/update-tool-versions.yml.
# Each update is appended as a markdown list item to the file named by $CHANGES_FILE (default: /dev/null).
set -euo pipefail

CHANGES_FILE="${CHANGES_FILE:-/dev/null}"

CLIENT_POM="madnessfeed-client/pom.xml"
SERVER_POM="madnessfeed-server/pom.xml"
DOCKERFILE="madnessfeed-server/src/main/docker/Dockerfile.native"
WRAPPER_PROPERTIES=".mvn/wrapper/maven-wrapper.properties"
# Tianon Gravi's signing key for gosu releases, as published in https://github.com/tianon/gosu#installation
GOSU_KEY_FINGERPRINT="B42F6819007F00F88E364FD4036A9C25BF357DD4"

# true when $2 is a higher version than $1
is_newer() {
	[ "$1" != "$2" ] && [ "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -n1)" = "$2" ]
}

# prints the value of a <name>value</name> Maven property
pom_property() {
	sed -n "s|.*<$2>\(.*\)</$2>.*|\1|p" "$1" | head -n1
}

# prints the highest version of a Maven Central artifact matching the given regex
maven_central_latest() {
	curl -fsSL "https://repo.maven.apache.org/maven2/$1/maven-metadata.xml" |
		sed -n 's|.*<version>\(.*\)</version>.*|\1|p' | grep -E "$2" | sort -V | tail -n1
}

record() {
	echo "Updating $1 from $2 to $3"
	echo "- $1: \`$2\` → \`$3\`" >>"$CHANGES_FILE"
}

# Node.js used by the client build: latest LTS release
current=$(pom_property "$CLIENT_POM" node.version)
latest=$(curl -fsSL https://nodejs.org/dist/index.json | jq -r '[.[] | select(.lts != false)][0].version')
if is_newer "${current#v}" "${latest#v}"; then
	record "Node.js (LTS)" "$current" "$latest"
	sed -i "s|<node.version>$current</node.version>|<node.version>$latest</node.version>|" "$CLIENT_POM"
fi

# npm used by the client build: latest release
current=$(pom_property "$CLIENT_POM" npm.version)
latest=$(curl -fsSL https://registry.npmjs.org/npm/latest | jq -r .version)
if is_newer "$current" "$latest"; then
	record "npm" "$current" "$latest"
	sed -i "s|<npm.version>$current</npm.version>|<npm.version>$latest</npm.version>|" "$CLIENT_POM"
fi

# google-java-format used by the spotless plugin: latest stable release. Spotless calls google-java-format through
# its internal API, so a new release can break the Spotless version Dependabot keeps us on (1.37.0 turned
# JavaFormatterOptions.Style into a record, which Spotless 3.10.3 fails on with NoSuchMethodError). The new version is
# kept only if spotless:apply runs with it, which also reformats the sources for any formatting changes it brings.
# Otherwise it's skipped until Dependabot updates Spotless.
current=$(pom_property "$SERVER_POM" google-java-format.version)
latest=$(maven_central_latest com/google/googlejavaformat/google-java-format '^[0-9]+\.[0-9]+(\.[0-9]+)?$')
if is_newer "$current" "$latest"; then
	sed -i "s|<google-java-format.version>$current</google-java-format.version>|<google-java-format.version>$latest</google-java-format.version>|" "$SERVER_POM"
	spotless_log=$(mktemp)
	if ./mvnw --batch-mode --no-transfer-progress --quiet --projects madnessfeed-server spotless:apply >"$spotless_log" 2>&1; then
		record "google-java-format" "$current" "$latest"
	else
		# the expected outcome until Spotless catches up, so its (long) output is folded away in the job log
		echo "::warning::Skipping google-java-format $latest: the spotless plugin can't run it yet"
		echo "::group::spotless:apply output with google-java-format $latest"
		cat "$spotless_log"
		echo "::endgroup::"
		git checkout -- madnessfeed-server
	fi
	rm -f "$spotless_log"
fi

# Maven wrapper: latest Maven 3.x (Maven 4 is a major upgrade, done by hand) and latest wrapper plugin
current_maven=$(sed -n 's|^distributionUrl=.*/apache-maven-\(.*\)-bin\.zip$|\1|p' "$WRAPPER_PROPERTIES")
latest_maven=$(maven_central_latest org/apache/maven/apache-maven '^3\.[0-9]+\.[0-9]+$')
current_wrapper=$(sed -n 's|^wrapperVersion=||p' "$WRAPPER_PROPERTIES")
latest_wrapper=$(maven_central_latest org/apache/maven/plugins/maven-wrapper-plugin '^[0-9]+\.[0-9]+\.[0-9]+$')
target_maven="$current_maven"
if is_newer "$current_maven" "$latest_maven"; then
	record "Maven (wrapper distribution)" "$current_maven" "$latest_maven"
	target_maven="$latest_maven"
fi
if is_newer "$current_wrapper" "$latest_wrapper"; then
	record "Maven wrapper" "$current_wrapper" "$latest_wrapper"
	# regenerates mvnw, mvnw.cmd and maven-wrapper.properties
	./mvnw --batch-mode --no-transfer-progress --non-recursive \
		"org.apache.maven.plugins:maven-wrapper-plugin:$latest_wrapper:wrapper" \
		-Dmaven="$target_maven" -Dtype=only-script
elif [ "$target_maven" != "$current_maven" ]; then
	sed -i "s|apache-maven/$current_maven/apache-maven-$current_maven-bin\.zip|apache-maven/$target_maven/apache-maven-$target_maven-bin.zip|" "$WRAPPER_PROPERTIES"
fi

# gosu in the Docker image: latest release, with the checksums of the signature-verified binaries
current=$(sed -n 's|^ENV GOSU_VERSION=||p' "$DOCKERFILE")
latest=$(git ls-remote --tags --refs https://github.com/tianon/gosu.git |
	sed 's|.*refs/tags/||' | grep -E '^[0-9]+\.[0-9]+(\.[0-9]+)?$' | sort -V | tail -n1)
if is_newer "$current" "$latest"; then
	record "gosu" "$current" "$latest"
	work=$(mktemp -d)
	trap 'rm -rf "$work"' EXIT
	export GNUPGHOME="$work/gnupg"
	mkdir -m 700 "$GNUPGHOME"
	curl -fsSL "https://keyserver.ubuntu.com/pks/lookup?op=get&options=mr&search=0x$GOSU_KEY_FINGERPRINT" |
		gpg --batch --quiet --import
	sed_args=(-e "s|^ENV GOSU_VERSION=$current$|ENV GOSU_VERSION=$latest|")
	for arch in amd64 arm64; do
		url="https://github.com/tianon/gosu/releases/download/$latest/gosu-$arch"
		curl -fsSL -o "$work/gosu-$arch" "$url"
		curl -fsSL -o "$work/gosu-$arch.asc" "$url.asc"
		# VALIDSIG ends with the primary key fingerprint, so this only accepts signatures made by Tianon's key
		if ! gpg --batch --status-fd 1 --verify "$work/gosu-$arch.asc" "$work/gosu-$arch" 2>/dev/null |
			grep -qE "^\[GNUPG:\] VALIDSIG .* $GOSU_KEY_FINGERPRINT$"; then
			echo "gosu-$arch $latest is not signed by $GOSU_KEY_FINGERPRINT" >&2
			exit 1
		fi
		sha=$(sha256sum "$work/gosu-$arch" | cut -d' ' -f1)
		sed_args+=(-e "s|^\(\s*$arch) echo \"\)[0-9a-f]\{64\}\(  /usr/local/bin/gosu\"\)|\1$sha\2|")
	done
	sed -i "${sed_args[@]}" "$DOCKERFILE"
fi
