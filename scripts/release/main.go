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

//go:embed release.tmpl
var releaseTmpl string

type changelogEntryKind int

const (
	feat changelogEntryKind = iota
	fix
	docs
	build
	chore
	other
)

func (k changelogEntryKind) toEmoji() string {
	switch k {
	case feat:
		return ":rocket:"
	case fix:
		return ":bug:"
	case docs:
		return ":books:"
	case build:
		return ":hammer:"
	case chore:
		return ":toolbox:"
	default:
		return ":shrug:"
	}
}

func (k changelogEntryKind) toTitle() string {
	switch k {
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

type changelogEntry struct {
	Hash    string
	Message string
	Kind    changelogEntryKind
	Tag     string
}

type tag struct {
	name string
	hash string
}

type changelog struct {
	Entries map[string]map[changelogEntryKind][]changelogEntry
	Tags    []string
}

type release struct {
	changelog     changelog
	version       string
	dockerVersion string
	dryRun        bool
	branchName    string
}

func parseHistory(version string) changelog {
	cmd := exec.Command("git", "log", "--pretty=format:\"%h,%s,%D\"")
	output, err := cmd.Output()
	if err != nil {
		fmt.Println("error checking commits:", err)
		os.Exit(1)
	}

	commits := strings.Split(strings.TrimSuffix(string(output), "\n"), "\n")

	entries := make([]changelogEntry, 0, len(commits))
	currentTag := version

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

	indexedEntries := make(map[string]map[changelogEntryKind][]changelogEntry)
	for _, entry := range entries {
		if _, ok := indexedEntries[entry.Tag]; !ok {
			indexedEntries[entry.Tag] = make(map[changelogEntryKind][]changelogEntry)

		}
		indexedEntries[entry.Tag][entry.Kind] = append(indexedEntries[entry.Tag][entry.Kind], entry)
	}

	return changelog{
		Entries: indexedEntries,
		Tags:    allTags,
	}
}

func newRelease(dryRun bool, action string) *release {
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
		if action == "start" {
			highestMicro++
		}
	}

	version := fmt.Sprintf("%s+%d", currentDate, highestMicro)

	return &release{
		changelog:     parseHistory(version),
		version:       version,
		dockerVersion: strings.Replace(version, "+", ".", -1),
		dryRun:        dryRun,
		branchName:    fmt.Sprintf("release/%s", version),
	}
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

func parseKind(kind string) changelogEntryKind {
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

func formatKind(rich bool) func(k changelogEntryKind) string {
	if rich {
		return func(k changelogEntryKind) string {
			return fmt.Sprintf("%s %s", k.toEmoji(), k.toTitle())
		}
	} else {
		return func(k changelogEntryKind) string {
			return k.toTitle()
		}
	}
}

var tagReg = regexp.MustCompile(`tag: (\d{4}.\d{2}.\d{2}\+\d+)`)

func (r *release) writeChangelog(fileName string, richFormat bool) {
	funcMap := template.FuncMap{
		"tocLink":    strings.NewReplacer(".", "", "+", "").Replace,
		"formatKind": formatKind(richFormat),
	}

	file, err := os.CreateTemp(os.TempDir(), "changelog")
	if err != nil {
		fmt.Printf("error creating temp file: %v\n", err)
		os.Exit(1)
	}
	defer os.Remove(file.Name())

	t, err := template.New("changelog").Funcs(funcMap).Parse(changelogTmpl)
	if err != nil {
		fmt.Printf("error parsing template: %v\n", err)
		os.Exit(1)
	}
	err = t.Execute(file, r.changelog)
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
	}

	err = file.Close()
	if err != nil {
		fmt.Printf("error closing file: %v\n", err)
		os.Exit(1)
	}

	if r.dryRun {
		fmt.Printf("os.Rename(%s, %s)\n", file.Name(), fileName)
	} else {
		err = os.Rename(file.Name(), fileName)
		if err != nil {
			fmt.Printf("error renaming file: %v\n", err)
			os.Exit(1)
		}
	}

	err = r.run("git", "add", fileName)
	if err != nil {
		fmt.Printf("error adding changelog: %v\n", err)
		os.Exit(1)
	}
}

func (r *release) updateChangelog() error {
	r.writeChangelog("CHANGELOG.md", false)
	r.writeChangelog("docs/docs/changelog.md", true)

	return r.run("git", "commit", "-m", fmt.Sprint("chore: release ", r.version))
}

func (r *release) createReleasePR() error {
	return r.run("gh", "pr", "create", "--title", fmt.Sprintf("chore: release %s", r.version), "--body", "See changelog", "--base", "main", "--head", r.branchName)
}

func (r *release) buildImages() error {
	if err := r.run("./gradlew", "--parallel", "-Pversion="+r.dockerVersion, ":tools:jibDockerBuild", ":server:jibDockerBuild"); err != nil {
		return err
	}

	if err := r.run("docker", "push", "typestream/tools-seeder:"+r.dockerVersion); err != nil {
		return err
	}

	return r.run("docker", "push", "typestream/server:"+r.dockerVersion)
}

func (r *release) releaseClient() error {
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

func (r *release) generateGitHubRelease() error {
	funcMap := template.FuncMap{"formatKind": formatKind(true)}

	file, err := os.CreateTemp(os.TempDir(), "release")
	if err != nil {
		fmt.Printf("error creating temp file: %v\n", err)
		os.Exit(1)
	}
	defer os.Remove(file.Name())

	t, err := template.New("release").Funcs(funcMap).Parse(releaseTmpl)
	if err != nil {
		fmt.Printf("error parsing template: %v\n", err)
		os.Exit(1)
	}
	err = t.Execute(file, r.changelog.Entries[r.version])
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
	}

	err = file.Close()
	if err != nil {
		fmt.Printf("error closing file: %v\n", err)
		os.Exit(1)
	}

	return r.run("gh", "release", "create", r.version, "--title", r.version, "-F", file.Name())
}

func (r *release) announceOnDiscord() error {
	return nil
}

func (r *release) start() {
	err := r.run("git", "checkout", "-b", r.branchName)
	if err != nil {
		fmt.Println("Error creating branch:", err)
		os.Exit(1)
	}

	err = r.updateChangelog()
	if err != nil {
		fmt.Println("Error updating changelog:", err)
		os.Exit(1)
	}

	err = r.run("git", "push", "--set-upstream", "origin", r.branchName)
	if err != nil {
		fmt.Println("Error pushing branch:", err)
		os.Exit(1)
	}

	err = r.createReleasePR()
	if err != nil {
		fmt.Println("Error creating PR:", err)
		os.Exit(1)
	}
}

func (r *release) publish() {
	err := r.run("git", "tag", r.version)
	if err != nil {
		fmt.Println("Error tagging:", err)
		os.Exit(1)
	}

	err = r.run("git", "push", "origin", r.version)
	if err != nil {
		fmt.Println("Error pushing tag:", err)
		os.Exit(1)
	}

	err = r.buildImages()
	if err != nil {
		fmt.Println("Error building images:", err)
		os.Exit(1)
	}

	err = r.releaseClient()
	if err != nil {
		fmt.Println("Error releasing client:", err)
		os.Exit(1)
	}

	err = r.generateGitHubRelease()
	if err != nil {
		fmt.Println("Error generating GitHub release:", err)
		os.Exit(1)
	}

	err = r.announceOnDiscord()
	if err != nil {
		fmt.Println("Error announcing on Discord:", err)
		os.Exit(1)
	}
}

func main() {
	var dry bool

	flag.BoolVar(&dry, "dry", false, "Dry run")
	flag.Parse()

	if flag.NArg() != 1 {
		fmt.Println("Usage: release [-dry] <start|publish>")
		os.Exit(1)
	}

	if dry {
		fmt.Println("dry run, not doing anything")
	}

	action := flag.Arg(0)

	r := newRelease(dry, action)
	switch action {
	case "start":
		r.start()
	case "publish":
		r.publish()
	default:
		fmt.Println("Usage: release [-dry] <start|publish>")
		os.Exit(1)
	}
}
