#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
outer_jar="${repo_root}/yudao-server/target/yudao-server.jar"
staging_dir="$(mktemp -d)"
trap 'rm -rf "${staging_dir}"' EXIT

cd "${repo_root}"
maven_profiles="${CLOUDMOLD_MAVEN_PROFILES:-cloudmold-agent-approval-bpm}"
mvn -B -ntp -pl yudao-server -am -P"${maven_profiles}" \
  -Dcloudmold.bpm.repackage.skip=true -DskipTests clean package

mkdir -p "${staging_dir}/BOOT-INF/lib"
replacements=()
while IFS= read -r -d '' original; do
  artifact="$(basename "${original}" .jar.original)"
  nested="$(unzip -Z1 "${outer_jar}" \
    | grep -E "^BOOT-INF/lib/${artifact}-.*SNAPSHOT\\.jar$" \
    | head -1 || true)"
  if [[ -n "${nested}" ]]; then
    cp "${original}" "${staging_dir}/${nested}"
    replacements+=("${nested}")
  fi
done < <(find "${repo_root}" -maxdepth 6 -path '*/target/*.jar.original' -print0)

if (( ${#replacements[@]} > 0 )); then
  zip -q -d "${outer_jar}" "${replacements[@]}"
  (
    cd "${staging_dir}"
    # Spring Boot nested libraries must be stored, not deflated.
    zip -q -0 "${outer_jar}" "${replacements[@]}"
  )
fi

unzip -tq "${outer_jar}"
printf 'Packaged runnable monolith: %s\n' "${outer_jar}"
