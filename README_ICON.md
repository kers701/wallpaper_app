# App icon — kers

Redesign based on hand sketch:

- **Background**: soft pink → white gradient
- **Shape**: oval with padding between lettering and border
- **Lettering**: large cursive **ke** + smaller **rs** (upper right), slightly tilted (not axis-aligned)
- **Text color**: light blue → light pink gradient
- **Border**: very pale blue gradient stroke

## Assets on `main`

| Path | Role |
|------|------|
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Adaptive foreground (oval + ke/rs) |
| `app/src/main/res/drawable/ic_launcher_background.xml` | Adaptive background gradient |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive icon |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | Round adaptive |
| `app/src/main/res/values/colors.xml` | `ic_launcher_background` = `#FFF5F8` |

API 26+ uses adaptive vector. For density PNG fallbacks, sync from local build artifacts if needed.
