# Third-party app icons

These icons are not mine and are not covered by this repository's GPLv3 licence.

Each is the property of the developer of the app it depicts, and may be a trademark.
They appear in the README only to identify which app a set of patches applies to
(nominative use). No affiliation, sponsorship or endorsement is claimed or implied.

Icons are fetched automatically by package name when the README is generated: Google Play
first, then Aptoide for apps that are off Play. Apps found on neither get a generated
initials badge (`<package>.svg`), retried on every run. To pin an icon, put your own
`<package>.png` here; real icons are never overwritten. To force an app's icon from a specific place, list it in `sources.json` in this
folder: `"package.name": "https://image-url"`, `"package.name": "page:https://site/"`
(takes that site's touch icon) or `"package.name": "og:https://store-page/"` (takes the
page's og:image). A list of these is tried in order. Listed apps are re-fetched every run and replace the
current icon; if the fetch fails the current icon stays.

## Removal

If you develop one of these apps and want your icon removed, open an issue and it
will be removed. The list reads fine without icons.
