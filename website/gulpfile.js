const browserSync = require("browser-sync").create();
const cached = require("gulp-cached");
const cleanCSS = require("clean-css");
const cssNano = require("gulp-cssnano");
const del = require("del");
const fileInclude = require("gulp-file-include");
const gulp = require("gulp");
const gulpIf = require("gulp-if");
const npmDist = require("gulp-npm-dist");
const replace = require("gulp-replace");
const uglify = require("gulp-uglify");
const useRef = require("gulp-useref-plus");
const rename = require("gulp-rename");
const sass = require("gulp-sass")(require("sass"));
const sourcemaps = require("gulp-sourcemaps");
const postcss = require("gulp-postcss");
const autoprefixer = require("autoprefixer");
const tailwindCss = require("tailwindcss");

const paths = {
  config: {
    tailwind: "./tailwind.config.js",
  },
  base: {
    base: {
      dir: "./",
    },
    node: {
      dir: "./node_modules",
    },
    packageLock: {
      files: "./package-lock.json",
    },
  },
  dist: {
    base: {
      dir: "./dist",
      files: "./dist/**/*",
    },
    libs: {
      dir: "./dist/assets/libs",
    },
    css: {
      dir: "./dist/assets/css",
    },
    js: {
      dir: "./dist/assets/js",
      files: "./dist/assets/js/pages",
    },
  },
  src: {
    base: {
      dir: "./src",
      files: "./src/**/*",
    },
    css: {
      dir: "./src/assets/css",
      files: "./src/assets/css/**/*",
    },
    html: {
      dir: "./src",
      files: "./src/**/*.html",
    },
    img: {
      dir: "./src/assets/images",
      files: "./src/assets/images/**/*",
    },
    js: {
      dir: "./src/assets/js",
      pages: "./src/assets/js/pages",
      files: "./src/assets/js/pages/*.js",
      main: "./src/assets/js/*.js",
    },
    partials: {
      dir: "./src/partials",
      files: "./src/partials/**/*",
    },
    scss: {
      dir: "./src/assets/scss",
      files: "./src/assets/scss/**/*",
      main: "./src/assets/scss/*.scss",
      icon: "./src/assets/scss/icons.scss",
    },
  },
};

gulp.task("browsersync", (callback) => {
  browserSync.init({
    server: {
      baseDir: [paths.dist.base.dir, paths.src.base.dir, paths.base.base.dir],
    },
  });
  callback();
});

gulp.task("browsersyncReload", (callback) => {
  browserSync.reload();
  callback();
});

gulp.task("watch", () => {
  gulp.watch(
    [paths.src.scss.files, "!" + paths.src.scss.icon],
    gulp.series("scss", "browsersyncReload")
  );
  gulp.watch(paths.src.scss.icon, gulp.series("icons", "browsersyncReload"));
  gulp.watch([paths.src.js.dir], gulp.series("js", "browsersyncReload"));
  gulp.watch([paths.src.js.pages], gulp.series("jsPages", "browsersyncReload"));
  gulp.watch(
    [paths.src.html.files, paths.src.partials.files],
    gulp.series(["fileinclude", "scss"], "browsersyncReload")
  );
});

gulp.task("js", () =>
  gulp.src(paths.src.js.main).pipe(gulp.dest(paths.dist.js.dir))
);

gulp.task("jsPages", () =>
  gulp.src(paths.src.js.files).pipe(gulp.dest(paths.dist.js.files))
);

const cssOptions = {
  compatibility: "*", // (default) - Internet Explorer 10+ compatibility mode
  inline: ["all"],
  level: 2,
};

gulp.task("scss", () =>
  gulp
    .src([paths.src.scss.main, "!" + paths.src.scss.icon])
    .pipe(sourcemaps.init())
    .pipe(sass().on("error", sass.logError))

    .pipe(postcss([tailwindCss(paths.config.tailwind), autoprefixer()]))
    .pipe(gulp.dest(paths.dist.css.dir))
    .on("data", function (file) {
      const bufferFile = new cleanCSS(cssOptions).minify(file.contents);
      return (file.contents = Buffer.from(bufferFile.styles));
    })
    .pipe(sourcemaps.write("./"))
    .pipe(gulp.dest(paths.dist.css.dir))
);

gulp.task("icons", () =>
  gulp
    .src(paths.src.scss.icon, { allowEmpty: true })
    .pipe(sass().on("error", sass.logError))
    .pipe(gulp.dest(paths.dist.css.dir))
    .on("data", (file) => {
      const bufferFile = new cleanCSS(cssOptions).minify(file.contents);
      return (file.contents = Buffer.from(bufferFile.styles));
    })
    .pipe(
      rename({
        suffix: ".min",
      })
    )
    .pipe(gulp.dest(paths.dist.css.dir))
);

gulp.task("fileinclude", () =>
  gulp
    .src([
      paths.src.html.files,
      "!" + paths.dist.base.files,
      "!" + paths.src.partials.files,
    ])
    .pipe(
      fileInclude({
        prefix: "@@",
        basepath: "@file",
        indent: true,
      })
    )
    .pipe(cached())
    .pipe(gulp.dest(paths.dist.base.dir))
);

gulp.task("clean:packageLock", (callback) => {
  del.sync(paths.base.packageLock.files);
  callback();
});

gulp.task("clean:dist", (callback) => {
  del.sync(paths.dist.base.dir);
  callback();
});

gulp.task("copy:all", () =>
  gulp
    .src([
      paths.src.base.files,
      "!" + paths.src.partials.dir,
      "!" + paths.src.partials.files,
      "!" + paths.src.scss.dir,
      "!" + paths.src.scss.files,
      "!" + paths.src.js.dir,
      "!" + paths.src.js.files,
      "!" + paths.src.js.main,
      "!" + paths.src.html.files,
    ])
    .pipe(gulp.dest(paths.dist.base.dir))
);

gulp.task("copy:libs", () =>
  gulp
    .src(npmDist(), { base: paths.base.node.dir })
    .pipe(
      rename(function (path) {
        path.dirname = path.dirname.replace(/\/dist/, "").replace(/\\dist/, "");
      })
    )
    .pipe(gulp.dest(paths.dist.libs.dir))
);

gulp.task("html", () =>
  gulp
    .src([
      paths.src.html.files,
      "!" + paths.dist.base.files,
      "!" + paths.src.partials.files,
    ])
    .pipe(
      fileInclude({
        prefix: "@@",
        basepath: "@file",
        indent: true,
      })
    )
    .pipe(replace(/href="(.{0,10})node_modules/g, 'href="$1assets/libs'))
    .pipe(replace(/src="(.{0,10})node_modules/g, 'src="$1assets/libs'))
    .pipe(useRef())
    .pipe(cached())
    .pipe(gulpIf("*.js", uglify()))
    .pipe(gulpIf("*.css", cssNano({ svgo: false })))
    .pipe(gulp.dest(paths.dist.base.dir))
);

gulp.task(
  "default",
  gulp.series(
    gulp.parallel(
      "clean:packageLock",
      "clean:dist",
      "copy:all",
      "copy:libs",
      "fileinclude",
      "scss",
      "icons",
      "js",
      "jsPages",
      "html"
    ),
    gulp.parallel("browsersync", "watch")
  )
);

gulp.task(
  "build",
  gulp.series(
    "clean:dist",
    "copy:all",
    "copy:libs",
    "fileinclude",
    "scss",
    "icons",
    "js",
    "jsPages",
    "html"
  )
);
