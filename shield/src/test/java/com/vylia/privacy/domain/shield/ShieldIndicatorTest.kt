package com.vylia.privacy.domain.shield

import org.junit.Assert.assertEquals
import org.junit.Test

class ShieldIndicatorTest {

    private fun derive(status: ShieldStatus, privateDns: Boolean = false, otherVpn: Boolean = false) =
        ShieldIndicator.derive(status, privateDns, otherVpn)

    @Test
    fun `le service en marche est actif, sauf si un DNS prive le contourne`() {
        assertEquals(ShieldIndicator(ShieldIndicatorState.ACTIVE), derive(ShieldStatus.ON))
        assertEquals(ShieldIndicator(ShieldIndicatorState.BYPASSED, ShieldBypass.PRIVATE_DNS), derive(ShieldStatus.ON, privateDns = true))
    }

    @Test
    fun `evince par un autre VPN toujours present, le bouclier est contourne`() {
        assertEquals(ShieldIndicator(ShieldIndicatorState.BYPASSED, ShieldBypass.OTHER_VPN), derive(ShieldStatus.REVOKED, otherVpn = true))
        assertEquals(ShieldIndicator.INACTIVE, derive(ShieldStatus.REVOKED))
    }

    @Test
    fun `eteint ou en echec, le bouclier est inactif meme avec un autre VPN ou un DNS prive`() {
        for (status in listOf(ShieldStatus.OFF, ShieldStatus.FAILED)) {
            assertEquals(ShieldIndicator.INACTIVE, derive(status))
            assertEquals(ShieldIndicator.INACTIVE, derive(status, privateDns = true, otherVpn = true))
        }
    }

    @Test
    fun `le demarrage reste en activation quel que soit le reseau`() {
        assertEquals(ShieldIndicator(ShieldIndicatorState.STARTING), derive(ShieldStatus.STARTING, privateDns = true, otherVpn = true))
    }

    @Test
    fun `chaque etat a son effet d'appui`() {
        assertEquals(ShieldTapEffect.DEACTIVATE, ShieldIndicator(ShieldIndicatorState.ACTIVE).tapEffect)
        assertEquals(ShieldTapEffect.ACTIVATE, ShieldIndicator.INACTIVE.tapEffect)
        assertEquals(ShieldTapEffect.NONE, ShieldIndicator(ShieldIndicatorState.STARTING).tapEffect)
        assertEquals(ShieldTapEffect.SHOW_BYPASS, ShieldIndicator(ShieldIndicatorState.BYPASSED, ShieldBypass.OTHER_VPN).tapEffect)
    }
}
