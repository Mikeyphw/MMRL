# Navigation: Activity as a primary destination

This patch promotes Activity out of the More sheet on compact navigation and moves Home access to the top-left app button.

## Compact phones

Bottom navigation now contains:

1. Repository
2. Modules
3. Recovery
4. Activity
5. More

Home remains available through the top-left app/event button on root workflow screens.

## Medium and expanded layouts

The navigation rail and permanent drawer omit Home from the destination list. Their header is the Home button. This keeps the adaptive navigation model consistent with the compact bottom bar.

## Back behavior

Nested screens continue to use their existing navigate-up toolbar. The Home button is only added to root workflow surfaces where it replaces the former bottom-navigation Home slot.
