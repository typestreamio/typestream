package main

import (
	"flag"
	"fmt"
	"os"
	"os/exec"
	"regexp"
	"sort"
	"strings"
	"text/template"
	"time"

	_ "embed"
)

//go:embed changelog.tmpl
var changelogTmpl string

type release struct {
	version       string
	dockerVersion string
	dryRun        bool
}

type changelogEntryType int

const (
	feat changelogEntryType = iota
	fix
	docs
	build
	chore
	other
)

type changelogEntry struct {
	Hash    string
	Message string
	Kind    changelogEntryType
	Tag     string
}

type tag struct {
	name string
	hash string
}

func (r *release) run(name string, args ...string) error {
	cmd := exec.Command(name, args...)
	if r.dryRun {
		fmt.Println(cmd.Args)
		return nil
	}

	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	return cmd.Run()
}

func newRelease(dryRun bool) *release {
	currentDate := time.Now().Format("2006.01.02")

	cmd := exec.Command("git", "tag", "--list", currentDate+"*")
	output, err := cmd.Output()
	if err != nil {
		fmt.Println("Error checking existing tags:", err)
		os.Exit(1)
	}

	existingTags := strings.Split(strings.TrimSuffix(string(output), "\n"), "\n")

	highestMicro := -1
	if len(existingTags) > 0 {
		sort.Slice(existingTags, func(i int, j int) bool { return existingTags[i] < existingTags[j] })

		lastTag := existingTags[len(existingTags)-1]
		parts := strings.Split(lastTag, "+")
		if len(parts) == 2 {
			micro := parts[1]
			microInt := 0
			_, err := fmt.Sscanf(micro, "%d", &microInt)
			if err == nil && microInt > highestMicro {
				highestMicro = microInt
			}
		}
	}

	if highestMicro == -1 {
		highestMicro = 0
	} else {
		highestMicro++
	}

	version := fmt.Sprintf("%s+%d", currentDate, highestMicro)

	return &release{
		version:       version,
		dockerVersion: strings.Replace(version, "+", ".", -1),
		dryRun:        dryRun}
}

func parseKind(kind string) changelogEntryType {
	switch kind {
	case "feat":
		return feat
	case "fix":
		return fix
	case "docs":
		return docs
	case "build":
		return build
	case "chore":
		return chore
	default:
		return other
	}
}

func formatKind(kind changelogEntryType) string {
	switch kind {
	case feat:
		return "Features"
	case fix:
		return "Bug Fixes"
	case docs:
		return "Documentation"
	case build:
		return "Build System"
	case chore:
		return "Chores"
	default:
		return "Other Changes"
	}
}

var tagReg = regexp.MustCompile(`^tag: (\d{4}.\d{2}.\d{2}\+\d+)$`)

func updateChangelog(r *release) error {
	cmd := exec.Command("git", "log", "--pretty=format:\"%h,%s,%D\"")
	output, err := cmd.Output()
	if err != nil {
		fmt.Println("error checking commits:", err)
		os.Exit(1)
	}

	commits := strings.Split(strings.TrimSuffix(string(output), "\n"), "\n")

	entries := make([]changelogEntry, 0, len(commits))
	currentTag := r.version

	allTags := make([]string, 0)
	allTags = append(allTags, currentTag)
	for _, commit := range commits {
		commitParts := strings.SplitN(strings.Trim(commit, "\""), ",", 3)

		hash := commitParts[0]
		message := commitParts[1]
		tag := commitParts[2]

		match := tagReg.FindStringSubmatch(tag)

		if len(match) == 2 {
			currentTag = match[1]
			allTags = append(allTags, currentTag)
		}

		messageParts := strings.SplitN(message, ":", 2)

		if len(messageParts) != 2 {
			fmt.Printf("invalid commit message: %s\n", message)
			continue
		}

		kind := parseKind(messageParts[0])
		commitMessage := strings.Trim(messageParts[1], " ")

		entry := changelogEntry{
			Hash:    hash,
			Message: commitMessage,
			Kind:    kind,
			Tag:     currentTag,
		}

		entries = append(entries, entry)
	}

	indexedEntries := make(map[string]map[changelogEntryType]changelogEntry)
	for _, entry := range entries {
		if _, ok := indexedEntries[entry.Tag]; !ok {
			indexedEntries[entry.Tag] = make(map[changelogEntryType]changelogEntry)
		}

		indexedEntries[entry.Tag][entry.Kind] = entry
	}

	funcMap := template.FuncMap{
		"replace":    strings.ReplaceAll,
		"formatKind": formatKind,
	}

	file, err := os.CreateTemp(os.TempDir(), "changelog")
	if err != nil {
		fmt.Printf("error creating temp file: %v\n", err)
		os.Exit(1)
	}
	defer os.Remove(file.Name())

	tmpl, err := template.New("changelog").Funcs(funcMap).Parse(changelogTmpl)
	if err != nil {
		fmt.Printf("error parsing template: %v\n", err)
		os.Exit(1)
	}
	err = tmpl.Execute(file, struct {
		Tags    []string
		Entries map[string]map[changelogEntryType]changelogEntry
	}{Tags: allTags, Entries: indexedEntries})
	if err != nil {
		fmt.Printf("error executing template: %v\n", err)
		os.Exit(1)
	}

	if r.dryRun {
		file.Seek(0, 0)
		buf := make([]byte, 1024)
		for {
			n, err := file.Read(buf)
			if err != nil {
				break
			}
			fmt.Print(string(buf[:n]))
		}

		return nil
	}

	err = file.Close()
	if err != nil {
		fmt.Printf("error closing file: %v\n", err)
		os.Exit(1)
	}

	err = os.Rename(file.Name(), "CHANGELOG.md")
	if err != nil {
		fmt.Printf("error renaming file: %v\n", err)
		os.Exit(1)
	}

	err = r.run("git", "add", "CHANGELOG.md")
	if err != nil {
		fmt.Printf("error adding changelog: %v\n", err)
		os.Exit(1)
	}

	return r.run("git", "commit", "-m", "chore: update changelog")
}

func gitTag(r *release) error {
	return r.run("git", "tag", r.version)
}

func buildImages(r *release) error {
	if err := r.run("./gradlew", "--parallel", "-Pversion="+r.dockerVersion, ":tools:jibDockerBuild", ":server:jibDockerBuild"); err != nil {
		return err
	}

	if err := r.run("docker", "push", "typestream/tools-seeder:"+r.dockerVersion); err != nil {
		return err
	}

	return r.run("docker", "push", "typestream/server:"+r.dockerVersion)
}

func releaseClient(r *release) error {
	cliPath := "./cli"

	cmd := exec.Command("goreleaser", "--clean")
	cmd.Dir = cliPath
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr

	if r.dryRun {
		fmt.Println(cmd.Args)
		return nil
	}

	return cmd.Run()
}

func generateGitHubRelease(release *release) error {
	return nil
}

func announceOnDiscord(release *release) error {
	return nil
}

func main() {
	var dry bool

	flag.BoolVar(&dry, "dry", false, "Dry run")
	flag.Parse()

	if dry {
		fmt.Println("dry run, not doing anything")
	}

	release := newRelease(dry)
	fmt.Printf("New version: %s\n", release.version)

	err := updateChangelog(release)
	if err != nil {
		fmt.Println("Error updating changelog:", err)
		os.Exit(1)
	}

	err = gitTag(release)
	if err != nil {
		fmt.Println("Error tagging:", err)
		os.Exit(1)
	}

	err = buildImages(release)
	if err != nil {
		fmt.Println("Error building images:", err)
		os.Exit(1)
	}

	err = releaseClient(release)
	if err != nil {
		fmt.Println("Error releasing client:", err)
		os.Exit(1)
	}

	err = generateGitHubRelease(release)
	if err != nil {
		fmt.Println("Error generating GitHub release:", err)
		os.Exit(1)
	}

	err = announceOnDiscord(release)
	if err != nil {
		fmt.Println("Error announcing on Discord:", err)
		os.Exit(1)
	}
}
