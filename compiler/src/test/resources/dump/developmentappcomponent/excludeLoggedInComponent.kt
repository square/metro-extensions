// MODULE: deps
package com.squareup.development.shell.login.screen

class LoginScreenModule

// MODULE: deps2
package com.squareup.development.shell.component

class DevelopmentLoggedInComponent

// MODULE: main(deps, deps2)
package com.test

import com.squareup.development.shell.DevelopmentAppComponent
import com.squareup.development.shell.DevelopmentApplication

@DevelopmentAppComponent(generateLoggedInComponent = false)
class MyApp : DevelopmentApplication()
