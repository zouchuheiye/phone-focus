"use strict";

const film = document.querySelector("#promo-film");
document.querySelectorAll("[data-play-film]").forEach((button) => {
  button.addEventListener("click", () => {
    document.querySelector("#film").scrollIntoView({
      behavior: matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth",
      block: "start",
    });
    film.play().catch(() => { film.focus(); });
  });
});
