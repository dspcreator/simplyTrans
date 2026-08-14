# Resource linking fix

The previous build failed because `Theme.AppCompat.Light.NoActionBar` was referenced
without an AppCompat dependency.

This version removes the unnecessary AppCompat dependency and uses the platform theme:

`@android:style/Theme.Material.Light.NoActionBar`

MainActivity extends `android.app.Activity`, so AppCompat is not required.
