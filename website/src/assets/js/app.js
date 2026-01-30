window.addEventListener("load", fn, false);

function fn() {
  // Menus
  activateMenu();
}

function toggleMenu() {
  document.getElementById("isToggle").classList.toggle("open");
  var isOpen = document.getElementById("navigation");
  if (isOpen.style.display === "block") {
    isOpen.style.display = "none";
  } else {
    isOpen.style.display = "block";
  }
}
/*********************/
/*    Menu Active    */
/*********************/
function getClosest(elem, selector) {
  if (!Element.prototype.matches) {
    Element.prototype.matches =
      Element.prototype.matchesSelector ||
      Element.prototype.mozMatchesSelector ||
      Element.prototype.msMatchesSelector ||
      Element.prototype.oMatchesSelector ||
      function (s) {
        var matches = (this.document || this.ownerDocument).querySelectorAll(s),
          i = matches.length;
        while (--i >= 0 && matches.item(i) !== this) {}
        return i > -1;
      };
  }

  // Get the closest matching element
  for (; elem && elem !== document; elem = elem.parentNode) {
    if (elem.matches(selector)) return elem;
  }
  return null;
}

function activateMenu() {
  var menuItems = document.getElementsByClassName("sub-menu-item");
  if (menuItems) {
    let matchingMenuItem = null;
    for (let idx = 0; idx < menuItems.length; idx++) {
      if (menuItems[idx].href === window.location.href) {
        matchingMenuItem = menuItems[idx];
      }
    }

    if (matchingMenuItem) {
      matchingMenuItem.classList.add("active");

      var immediateParent = getClosest(matchingMenuItem, "li");

      if (immediateParent) {
        immediateParent.classList.add("active");
      }

      let parent = getClosest(immediateParent, ".child-menu-item");
      if (parent) {
        parent.classList.add("active");
      }

      parent = getClosest(parent || immediateParent, ".parent-menu-item");

      if (parent) {
        parent.classList.add("active");

        let parentMenuitem = parent.querySelector(".menu-item");
        if (parentMenuitem) {
          parentMenuitem.classList.add("active");
        }

        let parentOfParent = getClosest(parent, ".parent-parent-menu-item");
        if (parentOfParent) {
          parentOfParent.classList.add("active");
        }
      } else {
        var parentOfParent = getClosest(
          matchingMenuItem,
          ".parent-parent-menu-item"
        );
        if (parentOfParent) {
          parentOfParent.classList.add("active");
        }
      }
    }
  }
}
/*********************/
/*  Clickable manu   */
/*********************/
if (document.getElementById("navigation")) {
  var elements = document
    .getElementById("navigation")
    .getElementsByTagName("a");
  for (var i = 0, len = elements.length; i < len; i++) {
    elements[i].onclick = function (elem) {
      if (elem.target.getAttribute("href") === "javascript:void(0)") {
        var submenu = elem.target.nextElementSibling.nextElementSibling;
        submenu.classList.toggle("open");
      }
    };
  }
}
/*********************/
/*   Menu Sticky     */
/*********************/
function windowScroll() {
  const navbar = document.getElementById("topnav");
  if (navbar != null) {
    if (
      document.body.scrollTop >= 50 ||
      document.documentElement.scrollTop >= 50
    ) {
      navbar.classList.add("nav-sticky");
    } else {
      navbar.classList.remove("nav-sticky");
    }
  }
}

window.addEventListener("scroll", () => {
  windowScroll();
});
/*********************/
/*    Back To TOp    */
/*********************/

window.onscroll = function () {
  scrollFunction();
};

function scrollFunction() {
  var mybutton = document.getElementById("back-to-top");
  if (mybutton != null) {
    if (
      document.body.scrollTop > 500 ||
      document.documentElement.scrollTop > 500
    ) {
      mybutton.classList.add("block");
      mybutton.classList.remove("hidden");
    } else {
      mybutton.classList.add("hidden");
      mybutton.classList.remove("block");
    }
  }
}

function topFunction() {
  document.body.scrollTop = 0;
  document.documentElement.scrollTop = 0;
}

/*********************/
/*  Active Sidebar   */
/*********************/
(function () {
  var current = location.pathname.substring(
    location.pathname.lastIndexOf("/") + 1
  );
  if (current === "") return;
  var menuItems = document.querySelectorAll(".sidebar-nav a");
  for (var i = 0, len = menuItems.length; i < len; i++) {
    if (menuItems[i].getAttribute("href").indexOf(current) !== -1) {
      menuItems[i].parentElement.className += " active";
    }
  }
})();

/*********************/
/*   Feather Icons   */
/*********************/
feather.replace();

/*********************/
/*     Small Menu    */
/*********************/
try {
  var spy = new Gumshoe("#navmenu-nav a");
} catch (err) {}

/*********************/
/*      WoW Js       */
/*********************/
try {
  new WOW().init();
} catch (error) {}

/*************************/
/*      Contact Js       */
/*************************/

/*********************/
/* Dark & Light Mode */
/*********************/
try {
  function changeTheme(e) {
    e.preventDefault();
    const htmlTag = document.getElementsByTagName("html")[0];

    if (htmlTag.className.includes("dark")) {
      htmlTag.className = "light";
    } else {
      htmlTag.className = "dark";
    }
  }

  const switcher = document.getElementById("theme-mode");
  switcher?.addEventListener("click", changeTheme);

  const chk = document.getElementById("chk");

  chk.addEventListener("change", changeTheme);
} catch (err) {}

/*********************/
/* LTR & RTL Mode */
/*********************/
try {
  const htmlTag = document.getElementsByTagName("html")[0];
  function changeLayout(e) {
    e.preventDefault();
    const switcherRtl = document.getElementById("switchRtl");
    if (switcherRtl.innerText === "LTR") {
      htmlTag.dir = "ltr";
    } else {
      htmlTag.dir = "rtl";
    }
  }
  const switcherRtl = document.getElementById("switchRtl");
  switcherRtl?.addEventListener("click", changeLayout);
} catch (err) {}
