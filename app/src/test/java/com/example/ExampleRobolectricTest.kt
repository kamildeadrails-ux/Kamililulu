package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.Order
import com.example.model.OrderStatus
import com.example.model.ProfileRole
import com.example.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Заказы братьев", appName)
  }

  @Test
  fun `verify user profile creation and role`() {
    val admin = UserProfile(
      id = "kamil_admin",
      name = "Камиль (Админ)",
      role = ProfileRole.ADMIN,
      avatarEmoji = "👑",
      savedAddresses = listOf("Дом: Кухня", "Дом: Зал"),
      preferences = "Главный администратор"
    )

    assertEquals(ProfileRole.ADMIN, admin.role)
    assertTrue(admin.savedAddresses.contains("Дом: Кухня"))
  }

  @Test
  fun `verify order price proposal and lifecycle`() {
    // 1. Initial order created
    var order = Order(
      id = "ord_test_1",
      userId = "user_son",
      userName = "Камиль",
      userAvatarEmoji = "👦",
      title = "Пицца 4 сыра",
      description = "Тонкое тесто",
      targetStoreOrPlace = "Додо",
      deliveryAddress = "Моя комната",
      urgency = "Срочно",
      status = OrderStatus.PENDING_REVIEW
    )
    assertEquals(OrderStatus.PENDING_REVIEW, order.status)

    // 2. Admin quotes price
    order = order.copy(
      proposedPrice = 650.0,
      adminNote = "Без лука, со скидкой",
      status = OrderStatus.PRICE_PROPOSED
    )
    assertEquals(OrderStatus.PRICE_PROPOSED, order.status)
    assertEquals(650.0, order.proposedPrice ?: 0.0, 0.01)

    // 3. Buyer accepts price quote
    order = order.copy(status = OrderStatus.ACCEPTED)
    assertEquals(OrderStatus.ACCEPTED, order.status)

    // 4. Admin marks purchased & buyer pays in reality
    order = order.copy(
      status = OrderStatus.COMPLETED_PAID,
      isPaidInReality = true,
      paymentMethodNote = "Наличными лично в руки"
    )
    assertEquals(OrderStatus.COMPLETED_PAID, order.status)
    assertTrue(order.isPaidInReality)
    assertEquals("Наличными лично в руки", order.paymentMethodNote)
  }
}
