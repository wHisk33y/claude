package de.knuffi.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class HouseTest {
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private fun at(h: Int, day: Int = 10, min: Int = 0): Long = ZonedDateTime.of(2026, 3, day, h, min, 0, 0, zone).toInstant().toEpochMilli()
    private val t0 = at(9)

    private fun started(t: Long = t0): GameState {
        var s = Engine.perform(GameState(), Action.Start(Difficulty.RELAXED, "Mochi"), t, zone).state
        repeat(Engine.HATCH_TAPS) { s = Engine.perform(s, Action.HatchTap, t, zone).state }
        return s
    }

    private fun rich(s: GameState, level: Int = 20) = s.copy(coins = 10_000, pet = s.pet!!.copy(level = level), counters = s.counters.copy(maxLevel = level))

    // ------------------------------------------------------------------ rooms and doors

    @Test
    fun newGameStartsInTheHouse() {
        val s = started()
        assertTrue(House.enabled(s))
        assertEquals(House.START.toSet(), House.unlocked(s))
        assertEquals(RoomKind.LIVING, s.house.current)
        assertTrue(s.equipped[Slot.WALL] != null)
    }

    @Test
    fun doorsAndPaths() {
        val start = House.START.toSet()
        val living = House.doors(RoomKind.LIVING, start)
        assertEquals(setOf(RoomKind.KITCHEN, RoomKind.BEDROOM), living.map { it.to }.toSet())
        assertEquals(DoorSide.EAST, living.first { it.to == RoomKind.KITCHEN }.side)
        assertEquals(DoorSide.NORTH, living.first { it.to == RoomKind.BEDROOM }.side)
        assertEquals(listOf(RoomKind.BEDROOM, RoomKind.LIVING, RoomKind.KITCHEN), House.path(RoomKind.BEDROOM, RoomKind.KITCHEN, start))
        // with the bathroom there is a second way, both are two doors long
        val more = start + RoomKind.BATH
        assertEquals(3, House.path(RoomKind.BEDROOM, RoomKind.KITCHEN, more).size)
        // the garden is two rooms wide and has a door to the living room and one to the kitchen
        val all = RoomKind.entries.toSet()
        val garden = House.doors(RoomKind.GARDEN, all)
        assertEquals(DoorSide.NORTH, garden.first { it.to == RoomKind.LIVING }.side)
        assertEquals(0.25f, garden.first { it.to == RoomKind.LIVING }.pos, 0.001f)
        assertEquals(0.75f, garden.first { it.to == RoomKind.KITCHEN }.pos, 0.001f)
        assertEquals(DoorSide.WEST, garden.first { it.to == RoomKind.CRAFT }.side)
        // every room can be reached from the living room in the full house
        for (k in RoomKind.entries) assertTrue(House.path(RoomKind.LIVING, k, all).isNotEmpty())
    }

    @Test
    fun everyRoomKeepsItsOwnFurniture() {
        var s = rich(started())
        s = s.copy(owned = s.owned + "wall_stars")
        s = Engine.perform(s, Action.Equip("wall_stars"), t0 + 1000, zone).state
        assertEquals("wall_stars", s.equipped[Slot.WALL])
        s = Engine.perform(s, Action.MoveFurniture(Slot.PLANT, 0.12f), t0 + 1500, zone).state
        s = Engine.perform(s, Action.GoToRoom(RoomKind.KITCHEN), t0 + 2000, zone).state
        assertEquals(RoomKind.KITCHEN, s.house.current)
        assertFalse(s.equipped[Slot.WALL] == "wall_stars")
        assertTrue(s.furnitureOffsets.isEmpty())
        s = Engine.perform(s, Action.GoToRoom(RoomKind.LIVING), t0 + 3000, zone).state
        assertEquals("wall_stars", s.equipped[Slot.WALL])
        assertEquals(0.12f, s.furnitureOffsets[Slot.PLANT]!!, 0.0001f)
        // clothes stay on when changing rooms
        s = s.copy(owned = s.owned + "hat_bow")
        s = Engine.perform(s, Action.Equip("hat_bow"), t0 + 4000, zone).state
        s = Engine.perform(s, Action.GoToRoom(RoomKind.BEDROOM), t0 + 5000, zone).state
        assertEquals("hat_bow", s.equipped[Slot.HAT])
    }

    @Test
    fun careTakesThePetToTheRightRoom() {
        var s = rich(started())
        s = s.copy(pet = s.pet!!.copy(satiety = 30.0, energy = 40.0))
        s = Engine.perform(s, Action.Feed("apple"), t0 + 1000, zone).state
        assertEquals(RoomKind.KITCHEN, s.house.current)
        s = Engine.perform(s, Action.ToggleSleep, t0 + 2000, zone).state
        assertTrue(s.pet!!.sleeping)
        assertEquals(RoomKind.BEDROOM, s.house.current)
        // while asleep the pet stays in its bed
        s = Engine.perform(s, Action.GoToRoom(RoomKind.LIVING), t0 + 3000, zone).state
        assertEquals(RoomKind.BEDROOM, s.house.current)
        // without a bathroom bathing happens where the pet is
        s = Engine.perform(s, Action.ToggleSleep, t0 + 4000, zone).state
        s = Engine.perform(s, Action.GoToRoom(RoomKind.LIVING), t0 + 5000, zone).state
        s = s.copy(pet = s.pet!!.copy(hygiene = 20.0))
        s = Engine.perform(s, Action.Clean, t0 + 6000, zone).state
        assertEquals(RoomKind.LIVING, s.house.current)
        s = Engine.perform(s, Action.BuildRoom(RoomKind.BATH), t0 + 7000, zone).state
        s = s.copy(pet = s.pet!!.copy(hygiene = 20.0))
        s = Engine.perform(s, Action.Clean, t0 + 8000, zone).state
        assertEquals(RoomKind.BATH, s.house.current)
    }

    @Test
    fun buildingRooms() {
        var s = started()
        s = s.copy(coins = 100)
        s = Engine.perform(s, Action.BuildRoom(RoomKind.BATH), t0 + 1000, zone).state
        assertFalse(RoomKind.BATH in House.unlocked(s))
        s = rich(s, level = 4)
        assertNotNull(House.buildBlocker(s, RoomKind.GARDEN))
        // the craft room needs a neighbour first
        s = rich(s, level = 20)
        assertNotNull(House.buildBlocker(s, RoomKind.CRAFT))
        // settle level-up and achievement rewards first, so only the price is counted
        s = Engine.perform(s, Action.GoToRoom(RoomKind.LIVING), t0 + 1500, zone).state
        s = Engine.perform(s, Action.GoToRoom(RoomKind.LIVING), t0 + 1600, zone).state
        val built = House.build(s.copy(coins = s.coins - RoomKind.GARDEN.price), RoomKind.GARDEN)
        s = Engine.perform(s, Action.BuildRoom(RoomKind.GARDEN), t0 + 2000, zone).state
        assertTrue(RoomKind.GARDEN in House.unlocked(s))
        assertTrue(s.coins <= built.coins + 200)
        assertNull(House.buildBlocker(s, RoomKind.CRAFT))
        s = Engine.perform(s, Action.BuildRoom(RoomKind.CRAFT), t0 + 3000, zone).state
        assertEquals(5, House.unlocked(s).size)
    }

    @Test
    fun classicModeKeepsItsRoom() {
        var s = rich(started())
        s = s.copy(owned = s.owned + "wall_stars" + "wall_mint")
        s = Engine.perform(s, Action.Equip("wall_stars"), t0 + 1000, zone).state
        s = Engine.perform(s, Action.UpdateSettings(s.settings.copy(houseMode = false)), t0 + 2000, zone).state
        assertFalse(House.enabled(s))
        // the classic room still has the old wallpaper from before the house
        val classicWall = s.equipped[Slot.WALL]
        s = Engine.perform(s, Action.Equip("wall_mint"), t0 + 3000, zone).state
        s = Engine.perform(s, Action.UpdateSettings(s.settings.copy(houseMode = true)), t0 + 4000, zone).state
        assertTrue(House.enabled(s))
        assertEquals("wall_stars", s.equipped[Slot.WALL])
        s = Engine.perform(s, Action.UpdateSettings(s.settings.copy(houseMode = false)), t0 + 5000, zone).state
        assertEquals("wall_mint", s.equipped[Slot.WALL])
        assertFalse(classicWall == null)
        // feeding in the classic room does not move anything
        s = Engine.perform(s, Action.Feed("apple"), t0 + 6000, zone).state
        assertNull(House.roomFor(Action.Feed("apple"), s))
    }

    @Test
    fun residentsLiveInTheRoomsAndWander() {
        var s = started()
        val friend = Pet(name = "Blubbi", form = Form.DELFINO, bornAt = t0 - 50_000, hatchedAt = t0 - 50_000, level = 8, line = EggLine.MEER)
        s = s.copy(resting = listOf(friend))
        s = Engine.tick(s, t0 + 500, zone).state
        // nobody moves in on their own
        assertNull(s.house.residents[friend.id])
        s = Engine.perform(s, Action.SetResident(friend.id, true), t0 + 800, zone).state
        assertTrue(friend.id in s.house.home)
        s = Engine.tick(s, t0 + 1000, zone).state
        val room = s.house.residents[friend.id]
        assertNotNull(room)
        assertTrue(room in House.unlocked(s))
        // after a long day the friend has moved at least once
        val seen = mutableSetOf(room!!)
        var t = t0 + 1000
        repeat(40) {
            t += House.WANDER_MS
            s = Engine.tick(s, t, zone).state
            seen += s.house.residents.getValue(friend.id)
        }
        assertTrue(seen.size > 1)
        // a friend on a trip is not in the house
        s = s.copy(trips = listOf(Trip(friend.id, Destination.STRAND, t, t + 3_600_000L)))
        s = Engine.tick(s, t + 1000, zone).state
        assertNull(s.house.residents[friend.id])
        // moving back out of the house
        s = s.copy(trips = emptyList())
        s = Engine.tick(s, t + 2000, zone).state
        assertNotNull(s.house.residents[friend.id])
        s = Engine.perform(s, Action.SetResident(friend.id, false), t + 3000, zone).state
        assertNull(s.house.residents[friend.id])
        assertTrue(House.residentsIn(s, s.house.current).none { it.id == friend.id })
        // unknown pets are ignored
        val before = s
        s = Engine.perform(s, Action.SetResident(424242L, true), t + 4000, zone).state
        assertEquals(before.house.home, s.house.home)
    }

    @Test
    fun furnitureIsPlacedFreelyIn3d() {
        var s = rich(started())
        val room = s.house.current
        s = Engine.perform(s, Action.PlaceFurniture(room, Slot.BED, Spot(0.3f, 0.2f, 90f)), t0 + 1000, zone).state
        assertEquals(Spot(0.3f, 0.2f, 90f), House.spots(s, room)[Slot.BED])
        // limits and turning
        s = Engine.perform(s, Action.PlaceFurniture(room, Slot.DECO, Spot(1.4f, -0.5f, -45f)), t0 + 2000, zone).state
        assertEquals(Spot(1f, 0f, 315f), House.spots(s, room)[Slot.DECO])
        // walls and the room itself cannot be placed
        s = Engine.perform(s, Action.PlaceFurniture(room, Slot.WALL, Spot(0.5f, 0.5f)), t0 + 3000, zone).state
        assertNull(House.spots(s, room)[Slot.WALL])
        // every room keeps its own places, rooms not built yet cannot be furnished
        s = Engine.perform(s, Action.PlaceFurniture(RoomKind.KITCHEN, Slot.PLANT, Spot(0.1f, 0.1f)), t0 + 4000, zone).state
        s = Engine.perform(s, Action.PlaceFurniture(RoomKind.CRAFT, Slot.PLANT, Spot(0.1f, 0.1f)), t0 + 4500, zone).state
        assertEquals(Spot(0.1f, 0.1f), House.spots(s, RoomKind.KITCHEN)[Slot.PLANT])
        assertNull(House.spots(s, room)[Slot.PLANT])
        assertTrue(House.spots(s, RoomKind.CRAFT).isEmpty())
        // walking to another room keeps the places
        s = Engine.perform(s, Action.GoToRoom(RoomKind.KITCHEN), t0 + 5000, zone).state
        assertEquals(Spot(0.3f, 0.2f, 90f), House.spots(s, room)[Slot.BED])
        assertEquals(RoomKind.KITCHEN, House.shownRoom(s))
        // the classic room has its own places
        s = Engine.perform(s, Action.PlaceFurniture(null, Slot.TOY, Spot(0.7f, 0.4f)), t0 + 6000, zone).state
        assertEquals(Spot(0.7f, 0.4f), House.spots(s, null)[Slot.TOY])
        s = Engine.perform(s, Action.UpdateSettings(s.settings.copy(houseMode = false)), t0 + 7000, zone).state
        assertNull(House.shownRoom(s))
        // starting over
        s = Engine.perform(s, Action.ResetPlaces(room), t0 + 8000, zone).state
        assertTrue(House.spots(s, room).isEmpty())
        assertEquals(Spot(0.7f, 0.4f), House.spots(s, null)[Slot.TOY])
        s = Engine.perform(s, Action.ResetPlaces(null), t0 + 9000, zone).state
        assertTrue(House.spots(s, null).isEmpty())
    }

    @Test
    fun movingFurnitureIsLimited() {
        var s = started()
        s = Engine.perform(s, Action.MoveFurniture(Slot.DECO, 2f), t0 + 1000, zone).state
        assertEquals(House.MAX_OFFSET, s.furnitureOffsets[Slot.DECO]!!, 0.0001f)
        s = Engine.perform(s, Action.MoveFurniture(Slot.WALL, 0.1f), t0 + 2000, zone).state
        assertNull(s.furnitureOffsets[Slot.WALL])
        s = Engine.perform(s, Action.ResetFurniture, t0 + 3000, zone).state
        assertTrue(s.furnitureOffsets.isEmpty())
    }

    // ------------------------------------------------------------------ family

    @Test
    fun bestFriendsGetAFamilyEgg() {
        var s = started()
        val a = s.pet!!.copy(level = 15, form = Form.LUMI)
        val b = Pet(name = "Blubbi", form = Form.DELFINO, bornAt = t0 - 50_000, hatchedAt = t0 - 50_000, level = 12, line = EggLine.MEER)
        s = s.copy(pet = a, resting = listOf(b), friendship = mapOf(Friends.key(a.id, b.id) to 90))
        assertEquals(1, Family.couples(s).size)
        var t = t0
        var tries = 0
        while (s.familyEggs.isEmpty() && tries < 60) {
            t += Family.CHECK_MS + 1000
            s = Engine.tick(s, t, zone, userPresent = true).state
            s = s.copy(pet = s.pet!!.copy(satiety = 90.0, joy = 90.0, energy = 90.0, hygiene = 90.0, health = 100.0, sleeping = false))
            tries++
        }
        val egg = s.familyEggs.single()
        assertEquals(setOf(a.id, b.id), egg.parents.toSet())
        assertTrue(egg.line == EggLine.KNUFFEL || egg.line == EggLine.MEER)
        assertNotNull(egg.blend)
        s = s.copy(restSlots = 6)
        s = Engine.perform(s, Action.HatchFamilyEgg(egg.id, "Mini"), t + 1000, zone).state
        val baby = s.pet!!
        assertEquals("Mini", baby.name)
        assertTrue(baby.isEgg)
        assertEquals(egg.parents, baby.parents)
        assertEquals(egg.blend, baby.blend)
        assertTrue(s.familyEggs.isEmpty())
        // parents and their child are family, not a couple
        val grown = baby.copy(form = Form.LUMI, level = 15)
        assertTrue(Family.related(a, grown))
    }

    @Test
    fun smallAnimalsInheritTheirFur() {
        val rnd = kotlin.random.Random(3)
        val a = Pet(name = "A", form = Form.KUSCHELHASE, bornAt = 1, line = EggLine.HASE, fur = Skins.bunnyFurs[0].id)
        val b = Pet(name = "B", form = Form.SPRINTHASE, bornAt = 2, line = EggLine.HASE, fur = Skins.bunnyFurs[1].id)
        val egg = Family.make(a, b, 100, rnd)
        assertEquals(EggLine.HASE, egg.line)
        assertTrue(egg.fur == a.fur || egg.fur == b.fur)
        assertNull(egg.blend)
    }

    // ------------------------------------------------------------------ cards

    @Test
    fun cardsFromTheAlbumAndPacks() {
        var s = started()
        // the first creature is a card right away, and there are two packs to start with
        assertEquals(1, s.cards[s.pet!!.form.name])
        assertEquals(2, s.count(Cards.PACK))
        val before = s.cards.values.sum()
        s = Engine.perform(s, Action.UseItem(Cards.PACK), t0 + 1000, zone).state
        assertEquals(before + Cards.PACK_SIZE, s.cards.values.sum())
        assertEquals(1, s.count(Cards.PACK))
        // spare copies turn into glitter dust, crafting costs dust
        s = s.copy(cards = mapOf(Form.LUMI.name to 4, Form.BABY.name to 1))
        s = Engine.perform(s, Action.DustDuplicates, t0 + 2000, zone).state
        assertEquals(1, s.cards[Form.LUMI.name])
        assertEquals(3 * Cards.rarity(Form.LUMI).dust, s.cardDust)
        s = s.copy(cardDust = 1000)
        s = Engine.perform(s, Action.CraftCard(Form.DRAKO), t0 + 3000, zone).state
        assertEquals(1, s.cards[Form.DRAKO.name])
        assertEquals(1000 - Cards.rarity(Form.DRAKO).craft, s.cardDust)
    }

    @Test
    fun cardValuesAndPacks() {
        for (f in Cards.all) {
            val st = Cards.stats(f)
            for (stat in CardStat.entries) assertTrue(st[stat] in 5..99)
            assertEquals(st, Cards.stats(f))
        }
        // legends are stronger than babies
        assertTrue(Cards.stats(Form.AURELIUS).total > Cards.stats(Form.BABY).total)
        val rnd = kotlin.random.Random(9)
        repeat(50) {
            val pack = Cards.openPack(rnd)
            assertEquals(Cards.PACK_SIZE, pack.size)
            assertTrue(pack.any { Cards.rarity(it) != CardRarity.COMMON })
        }
        assertEquals(Cards.DUEL_ROUNDS, Cards.opponentDeck(rnd, 10).size)
    }

    @Test
    fun duelsGiveCoinsAndADailyPack() {
        var s = started()
        val coins = s.coins
        val packs = s.count(Cards.PACK)
        s = Engine.perform(s, Action.CardDuelFinished(3, 2), t0 + 1000, zone).state
        assertTrue(s.coins >= coins + Cards.duelCoins(3))
        assertEquals(packs + 1, s.count(Cards.PACK))
        s = Engine.perform(s, Action.CardDuelFinished(4, 1), t0 + 2000, zone).state
        assertEquals(packs + 1, s.count(Cards.PACK))
        assertEquals(2, s.counters.duelWins)
    }

    // ------------------------------------------------------------------ voice commands

    @Test
    fun spokenCommandsFindTheTrick() {
        assertEquals(Trick.SITZ, Tricks.match("Sitz!"))
        assertEquals(Trick.SITZ, Tricks.match("mochi sitz"))
        assertEquals(Trick.PFOTE, Tricks.match("Gib Pfötchen"))
        assertEquals(Trick.ROLLE, Tricks.match("Mach eine Rolle"))
        assertEquals(Trick.SALTO, Tricks.match("Salto rückwärts"))
        assertEquals(Trick.DREHEN, Tricks.match("dreh dich"))
        assertEquals(Trick.SPRUNG, Tricks.match("Hopp!"))
        assertEquals(Trick.MAENNCHEN, Tricks.match("Mach Männchen"))
        assertEquals(Trick.TANZ, Tricks.match("Tanz für mich"))
        assertNull(Tricks.match("Guten Morgen"))
        // every trick can be called with its own command
        for (t in Trick.entries) assertEquals(t, Tricks.match(t.command))
    }

    // ------------------------------------------------------------------ music

    @Test
    fun musicMakesHappyAndSongsAreKept() {
        var s = started()
        val coins = s.coins
        s = Engine.perform(s, Action.MusicPlayed(20), t0 + 1000, zone).state
        assertTrue(s.coins > coins)
        assertEquals(t0 + 1000, s.cooldowns["music"])
        s = Engine.perform(s, Action.MusicPlayed(20), t0 + 2000, zone).state
        // the reward has a cooldown
        assertEquals(t0 + 1000, s.cooldowns["music"])
        assertEquals(40, s.counters.notesPlayed)
        val song = Song("", List(10) { SongNote(Instrument.PIANO, it % 8, it * 300) })
        repeat(8) { i -> s = Engine.perform(s, Action.SaveSong(song), t0 + 3000 + i, zone).state }
        assertEquals(Music.MAX_SONGS, s.songs.size)
        assertEquals("Lied 8", s.songs.last().name)
        s = Engine.perform(s, Action.DeleteSong(0), t0 + 4000, zone).state
        assertEquals(Music.MAX_SONGS - 1, s.songs.size)
    }

    @Test
    fun stateWithEverythingSurvivesJson() {
        var s = rich(started())
        s = Engine.perform(s, Action.BuildRoom(RoomKind.BATH), t0 + 1000, zone).state
        s = Engine.perform(s, Action.MoveFurniture(Slot.TOY, -0.1f), t0 + 1500, zone).state
        s = Engine.perform(s, Action.PlaceFurniture(RoomKind.BATH, Slot.RUG, Spot(0.4f, 0.6f, 45f)), t0 + 1600, zone).state
        s = Engine.perform(s, Action.PlaceFurniture(null, Slot.LAMP, Spot(0.2f, 0.1f)), t0 + 1700, zone).state
        s = s.copy(house = s.house.copy(home = setOf(7L, 9L)))
        s = s.copy(
            familyEggs = listOf(FamilyEgg(5, EggLine.MEER, listOf(1, 2), listOf("A", "B"), "LUMI")),
            songs = listOf(Song("Hallo", listOf(SongNote(Instrument.DRUMS, 1, 0)))),
            lastAutoBackup = 42,
        )
        val back = GameJson.decode(GameJson.encode(s))!!
        assertEquals(s.house, back.house)
        assertEquals(s.furnitureOffsets, back.furnitureOffsets)
        assertEquals(s.familyEggs, back.familyEggs)
        assertEquals(s.songs, back.songs)
        assertEquals(s.cards, back.cards)
        assertEquals(42L, back.lastAutoBackup)
    }
}
