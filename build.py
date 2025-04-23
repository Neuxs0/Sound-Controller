import os
import subprocess
import shutil
import glob
import platform
import sys
import argparse
import json
import re
import time

PROJECT_ROOT = os.path.dirname(os.path.abspath(__file__))
CONFIG_FILE = os.path.join(PROJECT_ROOT, 'build_config.json')
GRADLE_PROPERTIES_FILE = os.path.join(PROJECT_ROOT, 'gradle.properties')
DEFAULT_DIST_DIR = os.path.join(PROJECT_ROOT, 'dist')

BUILD_INFO = {
    "universal": {
        "dir": os.path.join('build', 'libs'),
        "task": "universalJar",
        "classifier": "universal",
        "jar_pattern": "{mod_name}-{version}-universal.jar"
    },
    "puzzle": {
        "dir": os.path.join('src', 'puzzle', 'build', 'libs'),
        "task": ":src:puzzle:shadowJar",
        "classifier": "puzzle",
        "jar_pattern": "{mod_name}-{version}-puzzle.jar"
    },
    "quilt": {
        "dir": os.path.join('src', 'quilt', 'build', 'libs'),
        "task": ":src:quilt:jar",
        "classifier": "quilt",
        "jar_pattern": "{mod_name}-quilt-{version}.jar"
    },
}


STANDARD_CLEAN_FOLDERS_REL = [
    'build',
    os.path.join('src', 'build'),
    os.path.join('src', 'common', 'build'),
    os.path.join('src', 'puzzle', '.gradle'),
    os.path.join('src', 'puzzle', 'build'),
    os.path.join('src', 'quilt', 'build')
]
CACHE_CLEAN_FOLDERS_REL = [
    '.gradle',
]

def load_config(config_path, verbose=False):
    defaults = {
        "enable_archiving": True,
        "archive_every_build": True,
        "archive_directory": "build_archive",
        "comment": "all, universal, puzzle, quilt",
        "build_targets": ["universal"],
        "build_naming_scheme": {
            "universal": "${mod_name}-${version}-universal.jar",
            "puzzle": "${mod_name}-${version}-puzzle.jar",
            "quilt": "${mod_name}-${version}-quilt.jar"
        },
        "custom_copy_paths": []
    }
    config_to_use = defaults.copy()
    if not os.path.exists(config_path):
        if verbose: print(f"Configuration file not found at '{config_path}'."); print("Creating a default configuration file...")
        try:
            with open(config_path, 'w') as f: json.dump(defaults, f, indent=4)
            if verbose: print(f"Default configuration saved to '{config_path}'."); print("Please review and customize settings.");
        except IOError as e: print(f"Error: Could not create configuration file '{config_path}': {e}", file=sys.stderr); print("Proceeding with built-in default configuration." if verbose else "", file=sys.stderr)
        except Exception as e: print(f"An unexpected error occurred while creating configuration file: {e}", file=sys.stderr); print("Proceeding with built-in default configuration." if verbose else "", file=sys.stderr)
    else:
        try:
            with open(config_path, 'r') as f: user_config = json.load(f)
            merged_config = defaults.copy()
            merged_config.update(user_config)

            if "build_targets" in user_config:
                 merged_config["build_targets"] = user_config["build_targets"]
            if "build_naming_scheme" in user_config and isinstance(user_config["build_naming_scheme"], dict):
                 merged_config["build_naming_scheme"] = defaults["build_naming_scheme"].copy()
                 merged_config["build_naming_scheme"].update(user_config["build_naming_scheme"])
            if "custom_copy_paths" in user_config:
                 merged_config["custom_copy_paths"] = user_config["custom_copy_paths"]

            config_to_use = merged_config
            if verbose: print(f"Loaded configuration from {config_path}")
        except json.JSONDecodeError as e: print(f"Error decoding JSON from {config_path}: {e}", file=sys.stderr); print("Using default configuration values where possible." if verbose else "", file=sys.stderr)
        except Exception as e: print(f"Error loading configuration from {config_path}: {e}", file=sys.stderr); print("Using default configuration values where possible." if verbose else "", file=sys.stderr)

    targets = config_to_use.get("build_targets", ["all"])
    if not isinstance(targets, list):
        targets = ["all"]
    if "all" in targets or not targets:
        config_to_use["effective_build_targets"] = list(BUILD_INFO.keys())
    else:
        config_to_use["effective_build_targets"] = [t for t in targets if t in BUILD_INFO]
        if not config_to_use["effective_build_targets"] and verbose:
             print(f"Warning: No valid build targets specified in {targets}. Nothing will be built.", file=sys.stderr)


    config_to_use["archive_directory_abs"] = os.path.join(PROJECT_ROOT, config_to_use.get("archive_directory", "build_archive"))
    config_to_use.pop("dev_build_marker", None); config_to_use.pop("archive_only_new_versions", None)
    config_to_use.pop("mod_name", None)

    return config_to_use

def get_mod_name_from_gradle_properties(properties_path, verbose=False):
    mod_name = None
    default_name = "UnknownMod"
    try:
        if not os.path.exists(properties_path):
            if verbose: print(f"Warning: gradle.properties not found at {properties_path}. Cannot read mod_name.", file=sys.stderr)
            return default_name

        with open(properties_path, 'r') as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith('#'): continue
                if line.startswith("mod_name") and '=' in line:
                    mod_name = line.split('=', 1)[1].strip()
                    break

        if mod_name:
            processed_name = mod_name.replace(' ', '_')
            if verbose: print(f"Read mod_name '{mod_name}' from gradle.properties, processed to '{processed_name}'.")
            return processed_name
        else:
            if verbose: print(f"Warning: 'mod_name' key not found in {properties_path}.", file=sys.stderr)
            return default_name

    except Exception as e:
        print(f"Error reading mod_name from {properties_path}: {e}", file=sys.stderr)
        return default_name

def get_gradle_executable():
    if platform.system() == "Windows": gradle_command = os.path.join(PROJECT_ROOT, 'gradlew.bat')
    else:
        gradle_command = os.path.join(PROJECT_ROOT, 'gradlew')
        try:
            if os.path.exists(gradle_command): os.chmod(gradle_command, 0o755)
        except OSError as e: print(f"Warning: Could not set executable permission on {gradle_command}: {e}", file=sys.stderr)
    return gradle_command

def extract_version_from_jar(jar_filename, expected_version):
    pattern = r'-({}(?:[.-][a-zA-Z0-9]+)*)(?:-[a-zA-Z0-9]+)?\.jar$'.format(re.escape(expected_version).split('-')[0])
    match = re.search(pattern, jar_filename)
    if match:
        if expected_version in match.group(1):
            return expected_version
        if expected_version == match.group(1):
             return expected_version

    match_general = re.search(r'-(\d+(\.\d+)*([.-][a-zA-Z0-9]+)*)(?:-[a-zA-Z0-9]+)?\.jar$', jar_filename)
    if match_general:
        return match_general.group(1)

    match_simple = re.match(r'^(\d+(\.\d+)*([.-][a-zA-Z0-9]+)*)\.jar$', jar_filename)
    if match_simple: return match_simple.group(1)

    return expected_version

def extract_version_from_gradle_properties(properties_path):
     try:
        with open(properties_path, 'r') as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith('#'): continue
                if line.startswith("mod_version") and '=' in line:
                    return line.split('=', 1)[1].strip()
     except Exception:
         pass
     return "UNKNOWN"

def format_filename(scheme, mod_name, subproject_type, version, original_filename):
    if not version: version = "UNKNOWN"
    if not mod_name: mod_name = "UnknownMod"
    basename = os.path.splitext(original_filename)[0]
    formatted = scheme
    formatted = formatted.replace('${mod_name}', mod_name)
    formatted = formatted.replace('${version}', version)
    formatted = formatted.replace('${subproject}', subproject_type)
    formatted = formatted.replace('${original_basename}', basename)
    formatted = re.sub(r'[<>:"/\\|?* ]', '_', formatted)
    if not formatted.lower().endswith('.jar'):
        formatted += '.jar'
    return formatted

def parse_version(version_string):
    if not version_string: return None
    dev_match = re.match(r'^(\d+(\.\d+)*)-Dev(\d+)$', version_string, re.IGNORECASE)
    if dev_match:
        base = dev_match.group(1)
        num = int(dev_match.group(3))
        return {'is_dev': True, 'base_version': base, 'dev_number': num, 'full_version': version_string}
    release_match = re.match(r'^(\d+(\.\d+)*)$', version_string)
    if release_match:
        return {'is_dev': False, 'base_version': version_string, 'dev_number': None, 'full_version': version_string}
    return {'is_dev': False, 'base_version': version_string, 'dev_number': None, 'full_version': version_string, 'unknown_format': True}

def run_gradle(config, verbose=False):
    gradle_executable = get_gradle_executable()
    if not os.path.exists(gradle_executable): print(f"Error: Could not find Gradle wrapper '{gradle_executable}'.", file=sys.stderr); sys.exit(1)

    targets_to_run = config.get("effective_build_targets", [])
    gradle_tasks = []

    if not targets_to_run:
         print("No valid build targets specified. Skipping Gradle execution.", file=sys.stderr)
         return

    is_building_all = (len(targets_to_run) == len(BUILD_INFO))

    if is_building_all:
        gradle_tasks = ['build']
        if verbose: print("Gradle target: build (all specified targets)")
    else:
        for target_name in targets_to_run:
            if target_name in BUILD_INFO:
                gradle_tasks.append(BUILD_INFO[target_name]["task"])

        gradle_tasks = list(set(gradle_tasks))
        if verbose: print(f"Gradle targets: {', '.join(gradle_tasks)}")


    gradle_args = gradle_tasks;
    if verbose: gradle_args.append('--info')
    command = [gradle_executable] + gradle_args; print(f"\nRunning Gradle command: {' '.join(command)}" if verbose else "", end="")
    try:
        process = subprocess.Popen(command, cwd=PROJECT_ROOT, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding='utf-8', errors='replace')
        stdout, stderr = process.communicate()
        if verbose: print("\n--- Gradle Standard Output ---"); print(stdout); print("-----------------------------"); print("\n--- Gradle Standard Error ---" if stderr else "", end=""); print(stderr if stderr else "", end=""); print("\n----------------------------" if stderr else "", end="")
        if process.returncode != 0:
            print("\n-------------------- GRADLE BUILD FAILED --------------------", file=sys.stderr); print(f"Command: {' '.join(command)}", file=sys.stderr); print(f"Return Code: {process.returncode}", file=sys.stderr)
            if not verbose: print("\n--- Gradle Standard Output ---", file=sys.stderr); print(stdout, file=sys.stderr); print("\n--- Gradle Standard Error ---", file=sys.stderr); print(stderr, file=sys.stderr)
            print("-----------------------------------------------------------", file=sys.stderr); sys.exit(1)
        elif verbose: print("\nGradle build successful.")
    except FileNotFoundError: print(f"Error: Could not execute Gradle wrapper '{gradle_executable}'.", file=sys.stderr); sys.exit(1)
    except Exception as e: print(f"An unexpected error occurred while running Gradle: {e}", file=sys.stderr); sys.exit(1)

def copy_and_rename_artifacts(config, mod_name, mod_version, verbose=False):
    dist_dir = DEFAULT_DIST_DIR; os.makedirs(dist_dir, exist_ok=True)
    if verbose: print(f"\nProcessing build artifacts..."); print(f"Preparing distribution directory: {dist_dir}")
    copied_files_details = []; found_any = False
    naming_schemes = config.get("build_naming_scheme", {})
    custom_copy_rules = config.get("custom_copy_paths", [])
    targets_built = config.get("effective_build_targets", [])

    if not targets_built:
        print("\nNo build targets were executed, skipping artifact processing.")
        return []

    for target_name in targets_built:
        target_info = BUILD_INFO.get(target_name)
        if not target_info: continue

        build_lib_dir_rel = target_info["dir"]
        build_lib_dir_abs = os.path.join(PROJECT_ROOT, build_lib_dir_rel)

        if not os.path.isdir(build_lib_dir_abs):
            if verbose: print(f"Build directory not found for target '{target_name}', skipping: {build_lib_dir_abs}"); continue
        if verbose: print(f"Checking for '{target_name}' JAR in {build_lib_dir_abs}...")

        expected_jar_name = target_info["jar_pattern"].format(mod_name=mod_name, version=mod_version)
        jar_search_pattern = os.path.join(build_lib_dir_abs, expected_jar_name)
        jars_found = glob.glob(jar_search_pattern)

        if not jars_found and target_info.get("classifier"):
             fallback_pattern = os.path.join(build_lib_dir_abs, f"*-{target_info['classifier']}.jar")
             if verbose: print(f"  Specific JAR '{expected_jar_name}' not found, trying pattern: {fallback_pattern}")
             jars_found = glob.glob(fallback_pattern)
             jars_found = [j for j in jars_found if '-sources' not in os.path.basename(j) and '-javadoc' not in os.path.basename(j)]

        if not jars_found:
             fallback_pattern_any = os.path.join(build_lib_dir_abs, '*.jar')
             if verbose: print(f"  Classifier JAR not found, trying any JAR: {fallback_pattern_any}")
             jars_found = glob.glob(fallback_pattern_any)
             jars_found = [j for j in jars_found if '-sources' not in os.path.basename(j) and '-javadoc' not in os.path.basename(j) and '-plain' not in os.path.basename(j)]


        if jars_found:
            found_any = True
            source_jar_path = jars_found[0]
            if len(jars_found) > 1 and verbose:
                 print(f"  Warning: Found multiple potential JARs for target '{target_name}' in {build_lib_dir_abs}. Using '{os.path.basename(source_jar_path)}'. Found: {[os.path.basename(j) for j in jars_found]}")

            original_filename = os.path.basename(source_jar_path);
            file_version = extract_version_from_jar(original_filename, mod_version)
            if not file_version and verbose: print(f"  Warning: Could not reliably extract version from '{original_filename}', using '{mod_version}'")
            file_version = file_version or mod_version

            scheme = naming_schemes.get(target_name, "${mod_name}-${version}-${subproject}.jar")
            final_filename = format_filename(scheme, mod_name, target_name, file_version, original_filename);
            final_dist_path = os.path.join(dist_dir, final_filename)

            try:
                if verbose: print(f"  Copying '{original_filename}' -> '{final_filename}' to dist/")
                shutil.copy2(source_jar_path, final_dist_path)
                copied_files_details.append({
                    "source_path": source_jar_path,
                    "dist_path": final_dist_path,
                    "subproject": target_name,
                    "version": file_version,
                    "final_filename": final_filename
                })
            except Exception as e: print(f"  Error copying {original_filename} to {dist_dir}: {e}", file=sys.stderr)

            for rule in custom_copy_rules:
                rule_targets = rule.get("targets", []); rule_dest = rule.get("destination")
                if target_name in rule_targets and rule_dest:
                    abs_rule_dest = os.path.abspath(os.path.join(PROJECT_ROOT, rule_dest)) if not os.path.isabs(rule_dest) else rule_dest;
                    custom_dest_path = os.path.join(abs_rule_dest, final_filename)
                    try:
                        os.makedirs(abs_rule_dest, exist_ok=True)
                        if verbose: print(f"  Copying '{final_filename}' to custom path per rule: {abs_rule_dest}")
                        shutil.copy2(final_dist_path, custom_dest_path)
                    except Exception as e: print(f"  Error copying {final_filename} to custom path {abs_rule_dest}: {e}", file=sys.stderr)

        elif verbose: print(f"  No relevant JARs found for target '{target_name}' in {build_lib_dir_abs}")

    if not found_any: print(f"\nWarning: No JAR files were found for the specified build targets: {targets_built}", file=sys.stderr)
    elif not copied_files_details: print(f"\nWarning: Found potential JARs but failed to copy any.", file=sys.stderr)
    elif verbose: print(f"\nSuccessfully processed {len(copied_files_details)} artifact(s) into {dist_dir}.")
    return copied_files_details


def archive_build(copied_files_details, config, verbose=False):
    if not config.get("enable_archiving", False):
        if verbose: print("\nArchiving is disabled in configuration. Skipping.")
        return
    if not copied_files_details:
        if verbose: print("\nNo artifacts were copied to dist, skipping archiving.")
        return

    if verbose: print("\nArchiving build...")

    archive_base_dir_abs = config["archive_directory_abs"]
    archive_every_build = config.get("archive_every_build", False)

    primary_version_string = None
    universal_version = None
    other_versions = set()

    for details in copied_files_details:
        version = details.get("version")
        if version:
            if details.get("subproject") == "universal":
                universal_version = version
            other_versions.add(version)

    if universal_version:
        primary_version_string = universal_version
        if len(other_versions) > 1 and verbose:
             print(f"  Warning: Multiple versions found across artifacts ({other_versions}). Using universal version '{primary_version_string}' for archive structure.")
    elif other_versions:
        primary_version_string = list(other_versions)[0]
        if len(other_versions) > 1 and verbose:
             print(f"  Warning: Multiple versions found across artifacts ({other_versions}), no universal JAR. Using '{primary_version_string}' for archive structure.")
    else:
        primary_version_string = "UNKNOWN_VERSION"; print("\nWarning: Could not determine build version from artifacts. Archiving under 'UNKNOWN_VERSION'.", file=sys.stderr)


    version_info = parse_version(primary_version_string)
    if not version_info:
        if verbose: print(f"Warning: Version string '{primary_version_string}' parse failed unexpectedly. Treating as unknown.", file=sys.stderr)
        version_info = {'is_dev': False, 'base_version': primary_version_string, 'dev_number': None, 'full_version': primary_version_string, 'unknown_format': True}


    base_version_name = version_info['base_version']
    safe_base_version_name = re.sub(r'[<>:"/\\|?* ]', '_', base_version_name)
    version_group_path = os.path.join(archive_base_dir_abs, safe_base_version_name)
    os.makedirs(version_group_path, exist_ok=True)

    if verbose: print(f"Archiving for Base Version: {base_version_name}"); print(f"  Base archive path: {version_group_path}")

    is_dev = version_info['is_dev']

    if is_dev:
        dev_builds_base_path = os.path.join(version_group_path, "dev_builds")
        specific_latest_path = os.path.join(dev_builds_base_path, "latest")
        os.makedirs(dev_builds_base_path, exist_ok=True)
        latest_type_msg = "dev 'latest'"
    else:
        specific_latest_path = os.path.join(version_group_path, "latest")
        latest_type_msg = "release 'latest'" if not version_info.get('unknown_format') else "'latest' for unknown format"

    if verbose: print(f"  Updating {latest_type_msg} at: {specific_latest_path}")
    try:
        if os.path.isdir(specific_latest_path): shutil.rmtree(specific_latest_path, ignore_errors=True)
        os.makedirs(specific_latest_path, exist_ok=True)
        copied_count = 0
        for artifact_info in copied_files_details:
            dest_path = os.path.join(specific_latest_path, artifact_info["final_filename"])
            try:
                shutil.copy2(artifact_info["dist_path"], dest_path)
                copied_count += 1
            except Exception as e: print(f"    Error copying {artifact_info['final_filename']} to {latest_type_msg}: {e}", file=sys.stderr)
        if verbose: print(f"    Updated {copied_count} artifact(s) in {latest_type_msg}.")
    except Exception as e: print(f"  Error updating {latest_type_msg} directory {specific_latest_path}: {e}", file=sys.stderr)

    if archive_every_build:
        if is_dev:
             if version_info['dev_number'] is not None:
                 try:
                     dev_build_instance_name = str(version_info['dev_number']).zfill(3)
                     specific_dev_build_archive_path = os.path.join(dev_builds_base_path, dev_build_instance_name)
                     if verbose: print(f"  Archiving specific dev build instance to: {specific_dev_build_archive_path} (using version number)")
                     os.makedirs(specific_dev_build_archive_path, exist_ok=True)

                     copied_count = 0
                     for artifact_info in copied_files_details:
                         dest_path = os.path.join(specific_dev_build_archive_path, artifact_info["final_filename"])
                         try:
                            shutil.copy2(artifact_info["dist_path"], dest_path)
                            copied_count += 1
                         except Exception as e: print(f"    Error copying {artifact_info['final_filename']} to dev instance {dev_build_instance_name}: {e}", file=sys.stderr)
                     if verbose: print(f"    Archived/Overwritten {copied_count} artifact(s) to dev instance {dev_build_instance_name}.")
                 except Exception as e: print(f"  Error archiving specific dev build instance {version_info['dev_number']} inside dev_builds: {e}", file=sys.stderr)
             else:
                  print(f"  Error: Dev build detected but no dev number found for version '{primary_version_string}'. Cannot archive numbered dev build.", file=sys.stderr)

        try:
            all_builds_path = os.path.join(version_group_path, "all_builds")
            os.makedirs(all_builds_path, exist_ok=True)
            timestamp_instance_name = time.strftime("%Y%m%d_%H%M%S") + "_" + str(int(time.time() % 1000)).zfill(3)
            timestamp_archive_path = os.path.join(all_builds_path, timestamp_instance_name)
            build_type_msg = "dev" if is_dev else "release" if not version_info.get('unknown_format') else "unknown format"

            if verbose: print(f"  Archiving specific {build_type_msg} instance to: {timestamp_archive_path} (using timestamp in all_builds)")
            os.makedirs(timestamp_archive_path, exist_ok=True)
            copied_count = 0
            for artifact_info in copied_files_details:
                dest_path = os.path.join(timestamp_archive_path, artifact_info["final_filename"])
                try:
                    shutil.copy2(artifact_info["dist_path"], dest_path)
                    copied_count += 1
                except Exception as e: print(f"    Error copying {artifact_info['final_filename']} to timestamp instance {timestamp_instance_name}: {e}", file=sys.stderr)
            if verbose: print(f"    Archived {copied_count} artifact(s) to timestamp instance {timestamp_instance_name} in all_builds.")
        except Exception as e: print(f"  Error archiving build instance to all_builds: {e}", file=sys.stderr)

    elif verbose: print("  Skipping archiving of specific build instances ('archive_every_build' is false).")


def clean_build_folders(config, verbose=False, clean_cache=False):
    folders_to_clean_rel = STANDARD_CLEAN_FOLDERS_REL[:]

    if clean_cache:
        if verbose: print("Cache cleaning enabled, adding cache folders to clean list.")
        folders_to_clean_rel.extend(CACHE_CLEAN_FOLDERS_REL)
        clean_type = "build and cache"
    else:
        clean_type = "build output"

    unique_folders_to_clean_rel = set(folders_to_clean_rel)

    if verbose:
        print(f"\nPerforming {clean_type} cleanup...")
        print(f"Target relative paths for cleaning: {sorted(list(unique_folders_to_clean_rel))}")

    deleted_count = 0; skipped_count = 0; error_count = 0
    for folder_rel_path in sorted(list(unique_folders_to_clean_rel)):
        abs_folder_path = os.path.abspath(os.path.join(PROJECT_ROOT, folder_rel_path))

        if verbose: print(f"Attempting to clean path: {abs_folder_path} (Relative: {folder_rel_path})")

        if not os.path.exists(abs_folder_path) and not os.path.lexists(abs_folder_path):
            if verbose: print(f"  Path does not exist, skipping.")
            skipped_count += 1
            continue

        try:
            if os.path.isdir(abs_folder_path) and not os.path.islink(abs_folder_path):
                if verbose: print(f"  Path is a directory. Deleting tree...")
                shutil.rmtree(abs_folder_path, ignore_errors=False)
                if os.path.exists(abs_folder_path):
                    print(f"  ERROR: Failed to completely delete directory {abs_folder_path}. Check permissions/locks.", file=sys.stderr)
                    error_count += 1
                else:
                    if verbose: print("  Successfully deleted directory.")
                    deleted_count += 1
            elif os.path.isfile(abs_folder_path) or os.path.islink(abs_folder_path):
                if verbose: print(f"  Path is a file or link. Deleting...")
                os.remove(abs_folder_path)
                if os.path.exists(abs_folder_path):
                     print(f"  ERROR: Failed to completely delete file/link {abs_folder_path}. Check permissions/locks.", file=sys.stderr)
                     error_count += 1
                else:
                     if verbose: print("  Successfully deleted file/link.")
                     deleted_count += 1
            else:
                 if verbose: print(f"  Path exists but is neither file nor directory nor link, skipping: {abs_folder_path}")
                 skipped_count += 1

        except Exception as e:
             print(f"  ERROR deleting {abs_folder_path}: {e}", file=sys.stderr)
             error_count += 1
             if os.path.exists(abs_folder_path):
                  print(f"  Path still exists after error.", file=sys.stderr)


    if verbose:
        print(f"\nCleanup complete.")
        print(f"  Successfully removed: {deleted_count} item(s)")
        print(f"  Skipped non-existent: {skipped_count} path(s)")
        print(f"  Errors during deletion: {error_count} item(s)")
    elif error_count > 0:
         print(f"\nCleanup finished with {error_count} error(s). Run with -v for details.", file=sys.stderr)



if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Build the mod project with customizations.')
    parser.add_argument('-v', '--verbose', action='store_true', help='Enable detailed output for script and Gradle.')
    parser.add_argument('--clean', action='store_true', help='Perform standard cleanup (build folders, dist) before building.')
    parser.add_argument('-cc', '--clean-cache', action='store_true', help='Perform standard cleanup AND clear Gradle cache (.gradle folders) before building.')
    parser.add_argument('-t', '--targets', nargs='+', help='Specify build targets (universal, puzzle, quilt) overriding build_config.json. Use "all" for all.', default=None)


    args = parser.parse_args()
    verbose = args.verbose

    print("Starting build process...")

    config = load_config(CONFIG_FILE, verbose=verbose)

    if args.targets:
        if verbose: print(f"Overriding build targets from command line: {args.targets}")
        config["build_targets"] = args.targets
        if "all" in config["build_targets"] or not config["build_targets"]:
            config["effective_build_targets"] = list(BUILD_INFO.keys())
        else:
            config["effective_build_targets"] = [t for t in config["build_targets"] if t in BUILD_INFO]
            if not config["effective_build_targets"]:
                 print(f"Warning: No valid build targets specified via command line ({args.targets}). Nothing will be built.", file=sys.stderr)


    mod_name = get_mod_name_from_gradle_properties(GRADLE_PROPERTIES_FILE, verbose=verbose)
    mod_version = extract_version_from_gradle_properties(GRADLE_PROPERTIES_FILE)

    if os.path.exists(DEFAULT_DIST_DIR): shutil.rmtree(DEFAULT_DIST_DIR)
    if args.clean_cache:
        if verbose: print("Performing pre-build cleanup (including cache)...")
        clean_build_folders(config, verbose=verbose, clean_cache=True)
    elif args.clean:
        if verbose: print("Performing pre-build cleanup (standard only)...")
        clean_build_folders(config, verbose=verbose, clean_cache=False)


    if config.get("effective_build_targets"):
        if verbose: print(f"\nStarting Gradle build for targets: {config['effective_build_targets']}...")
        run_gradle(config, verbose=verbose)
        if verbose: print("Copying jars...")
        copied_files_details = copy_and_rename_artifacts(config, mod_name, mod_version, verbose=verbose)
        if verbose: print("Archiving versions...")
        archive_build(copied_files_details, config, verbose=verbose)
        if args.clean_cache:
            if verbose: print("Performing post-build cleanup (including cache)...")
            clean_build_folders(config, verbose=verbose, clean_cache=True)
        else:
            if verbose: print("Performing post-build cleanup (standard only)...")
            clean_build_folders(config, verbose=verbose, clean_cache=False)
    else:
        print("\nNo valid build targets configured. Skipping Gradle build, artifact processing, and archiving.")
        copied_files_details = []

    print("Build process finished.")