extends Node2D

const Courier = preload("res://scripts/courier.gd")
const LEVELS := [
    {"name":"First Light", "district":"OLD TOWN", "accent":"#ffbd63", "skill":"Jump", "gap":54, "height":22},
    {"name":"Awning Alley", "district":"OLD TOWN", "accent":"#ffbd63", "skill":"Momentum", "gap":64, "height":28},
    {"name":"Fire Escape", "district":"OLD TOWN", "accent":"#ffbd63", "skill":"Wall jump", "gap":72, "height":42},
    {"name":"Neon Signs", "district":"OLD TOWN", "accent":"#ffbd63", "skill":"Wall jump", "gap":78, "height":45},
    {"name":"Cable Crossing", "district":"GLASS MARKET", "accent":"#64e3d2", "skill":"Dash", "gap":92, "height":36},
    {"name":"Rain Check", "district":"GLASS MARKET", "accent":"#64e3d2", "skill":"Dash", "gap":102, "height":45},
    {"name":"The Long Drop", "district":"GLASS MARKET", "accent":"#64e3d2", "skill":"Air control", "gap":112, "height":52},
    {"name":"Billboard Run", "district":"GLASS MARKET", "accent":"#64e3d2", "skill":"Dash routes", "gap":120, "height":52},
    {"name":"Sky Garden", "district":"NORTH SPIRE", "accent":"#ad91ff", "skill":"Bounce pads", "gap":118, "height":60},
    {"name":"Cloudline", "district":"NORTH SPIRE", "accent":"#ad91ff", "skill":"Bounce + dash", "gap":126, "height":68},
    {"name":"Last Delivery", "district":"NORTH SPIRE", "accent":"#ad91ff", "skill":"Mastery run", "gap":132, "height":72},
    {"name":"Rooftop Sunrise", "district":"NORTH SPIRE", "accent":"#ad91ff", "skill":"Finale", "gap":138, "height":76}
]
const SAVE_PATH := "user://rooftop_run.cfg"

var ui: Control
var world: Node2D
var player: CharacterBody2D
var level_index := 0
var unlocked := 0
var coins := 0
var level_coins := 0
var checkpoint := Vector2(110, 520)
var paused := false
var completed := false
var screen_shake := 0.0
var camera: Camera2D
var hud_label: Label
var touch_pad: Control
var settings := {"touch_controls": true, "screen_shake": true}

func _ready() -> void:
    _load_save()
    _make_ui()
    _show_title()

func _process(delta: float) -> void:
    if screen_shake > 0.0:
        screen_shake = maxf(0.0, screen_shake - delta)
        if camera and settings.screen_shake:
            camera.offset = Vector2(randf_range(-5,5), randf_range(-3,3)) * screen_shake * 8.0
    elif camera:
        camera.offset = Vector2.ZERO

func _unhandled_input(event: InputEvent) -> void:
    if event.is_action_pressed("pause") and world and not completed:
        _toggle_pause()

func _make_ui() -> void:
    var layer := CanvasLayer.new()
    layer.layer = 5
    add_child(layer)
    ui = Control.new()
    ui.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    ui.process_mode = Node.PROCESS_MODE_ALWAYS
    layer.add_child(ui)

func _clear_ui() -> void:
    for child in ui.get_children():
        child.queue_free()

func _panel(parent: Control, width: float = 720.0) -> VBoxContainer:
    var center := CenterContainer.new()
    center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    parent.add_child(center)
    var panel := PanelContainer.new()
    panel.custom_minimum_size = Vector2(width, 0)
    var style := StyleBoxFlat.new()
    style.bg_color = Color("#111d31")
    style.border_color = Color("#304460")
    style.set_border_width_all(1)
    style.set_corner_radius_all(24)
    style.content_margin_left = 34
    style.content_margin_right = 34
    style.content_margin_top = 30
    style.content_margin_bottom = 30
    panel.add_theme_stylebox_override("panel", style)
    center.add_child(panel)
    var box := VBoxContainer.new()
    box.add_theme_constant_override("separation", 14)
    panel.add_child(box)
    return box

func _label(text: String, size: int, color: Color = Color("#f3f0e8")) -> Label:
    var label := Label.new()
    label.text = text
    label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
    label.add_theme_font_size_override("font_size", size)
    label.add_theme_color_override("font_color", color)
    return label

func _button(text: String, action: Callable, primary: bool = false) -> Button:
    var button := Button.new()
    button.text = text
    button.custom_minimum_size = Vector2(0, 54)
    button.add_theme_font_size_override("font_size", 19)
    var normal := StyleBoxFlat.new()
    normal.bg_color = Color("#ffbd63") if primary else Color("#1c2e48")
    normal.border_color = Color("#ffda9a") if primary else Color("#314766")
    normal.set_border_width_all(1)
    normal.set_corner_radius_all(16)
    normal.content_margin_top = 10
    normal.content_margin_bottom = 10
    button.add_theme_stylebox_override("normal", normal)
    var hover := normal.duplicate()
    hover.bg_color = Color("#ffd18b") if primary else Color("#263d5c")
    button.add_theme_stylebox_override("hover", hover)
    button.add_theme_stylebox_override("pressed", hover)
    button.add_theme_color_override("font_color", Color("#172135") if primary else Color("#f3f0e8"))
    button.pressed.connect(action)
    return button

func _show_title() -> void:
    _clear_ui()
    var box := _panel(ui, 690)
    box.add_child(_label("ROOFTOP RUN", 44, Color("#ffbd63")))
    box.add_child(_label("A courier. A city above the city. One more roof.", 18, Color("#b7c4d6")))
    box.add_child(_label("12 handcrafted-feeling stages · 3 movement skills · 3 districts", 15, Color("#7f93ad")))
    box.add_child(_button("CONTINUE  ·  LEVEL %02d" % (mini(unlocked, 11) + 1), _start_continue, true))
    box.add_child(_button("CHOOSE A LEVEL", _show_level_select))
    box.add_child(_button("SETTINGS", _show_settings))
    box.add_child(_label("Move: A / D or ← / →     Jump: Space     Dash: Shift     Pause: Esc", 13, Color("#90a4bd")))

func _start_continue() -> void:
    _start_level(mini(unlocked, 11))

func _show_level_select() -> void:
    _clear_ui()
    var box := _panel(ui, 820)
    box.add_child(_label("CHAPTER 01  /  ABOVE THE STREET", 25, Color("#ffbd63")))
    box.add_child(_label("Pick up the route where you left it.", 15, Color("#aebdd0")))
    var grid := GridContainer.new()
    grid.columns = 3
    grid.add_theme_constant_override("h_separation", 10)
    grid.add_theme_constant_override("v_separation", 10)
    box.add_child(grid)
    for i in range(12):
        var idx := i
        var info: Dictionary = LEVELS[i]
        var locked := idx > unlocked
        var button := _button(("🔒  " if locked else "%02d  " % (idx + 1)) + str(info.name), func(): _start_level(idx), not locked and idx == unlocked)
        button.disabled = locked
        button.custom_minimum_size = Vector2(225, 58)
        grid.add_child(button)
    box.add_child(_button("BACK", _show_title))

func _show_settings() -> void:
    _clear_ui()
    var box := _panel(ui, 600)
    box.add_child(_label("SETTINGS", 32, Color("#ffbd63")))
    var touch := CheckButton.new()
    touch.text = "On-screen touch controls"
    touch.button_pressed = settings.touch_controls
    touch.add_theme_font_size_override("font_size", 18)
    touch.toggled.connect(func(value: bool): settings.touch_controls = value; _save_settings())
    box.add_child(touch)
    var shake := CheckButton.new()
    shake.text = "Subtle impact camera shake"
    shake.button_pressed = settings.screen_shake
    shake.add_theme_font_size_override("font_size", 18)
    shake.toggled.connect(func(value: bool): settings.screen_shake = value; _save_settings())
    box.add_child(shake)
    box.add_child(_label("Progress saves on this device. Keyboard and touch both work.", 14, Color("#aebdd0")))
    box.add_child(_button("BACK", _show_title))

func _start_level(index: int) -> void:
    level_index = clampi(index, 0, 11)
    coins = 0
    level_coins = 0
    paused = false
    completed = false
    _clear_world()
    _build_level()
    _build_hud()
    get_tree().paused = false

func _clear_world() -> void:
    if world:
        world.queue_free()
    world = Node2D.new()
    world.name = "LevelWorld"
    add_child(world)
    move_child(world, 0)
    player = null
    camera = null

func _build_level() -> void:
    var data: Dictionary = LEVELS[level_index]
    world.set_meta("accent", Color(str(data.accent)))
    _build_backdrop()
    var floor_y := 620.0
    _platform(Vector2(0, floor_y), Vector2(360, 100))
    var x := 300.0
    var y := floor_y
    for i in range(14):
        var gap := float(data.gap) + float((i * 37 + level_index * 11) % 75)
        x += gap
        var rise := float(((i * 23 + level_index * 17) % int(data.height * 2 + 1)) - int(data.height * 0.55))
        y = clampf(y + rise, 390.0, 635.0)
        var width := maxf(118.0, 190.0 - level_index * 3.0 - float((i * 19) % 32))
        _platform(Vector2(x, y), Vector2(width, 48))
        if i % 3 == 1:
            _coin(Vector2(x + width * 0.5, y - 72 - float((i % 2) * 12)))
        if i % 4 == 2 and i > 2:
            _hazard(Vector2(x + width * 0.5, y - 7), minf(52.0, width * 0.38))
        if i == 4 or i == 9:
            _checkpoint_flag(Vector2(x + width * 0.35, y - 42))
        x += width
    _platform(Vector2(x + 130, y), Vector2(360, 48))
    _goal(Vector2(x + 260, y - 52))
    var start := Vector2(120, floor_y - 45)
    checkpoint = start
    player = Courier.new()
    player.name = "Courier"
    player.position = start
    player.wall_jump_unlocked = level_index >= 2
    player.dash_unlocked = level_index >= 4
    player.bounce_unlocked = level_index >= 8
    player.fell.connect(_respawn)
    world.add_child(player)
    var shape := CollisionShape2D.new()
    var capsule := CapsuleShape2D.new()
    capsule.radius = 12
    capsule.height = 44
    shape.shape = capsule
    player.add_child(shape)
    camera = Camera2D.new()
    camera.position_smoothing_enabled = true
    camera.position_smoothing_speed = 6.0
    camera.limit_left = 0
    camera.limit_right = int(x + 750)
    camera.limit_top = 0
    camera.limit_bottom = 720
    player.add_child(camera)
    player.z_index = 3

func _build_backdrop() -> void:
    var accent: Color = world.get_meta("accent")
    var sky := Polygon2D.new()
    sky.polygon = PackedVector2Array([Vector2(-500,-1000),Vector2(7000,-1000),Vector2(7000,720),Vector2(-500,720)])
    sky.color = Color("#101c34")
    world.add_child(sky)
    var sun := Polygon2D.new()
    sun.polygon = PackedVector2Array([Vector2(0,-150),Vector2(700,-150),Vector2(350,450)])
    sun.color = Color(accent.r,accent.g,accent.b,0.15)
    sun.position = Vector2(700,150)
    world.add_child(sun)
    for i in range(38):
        var h := float(120 + (i * 71 + level_index * 29) % 270)
        var w := float(110 + (i * 31) % 100)
        var building := Polygon2D.new()
        var bx := float(i * 205 - 250)
        building.polygon = PackedVector2Array([Vector2.ZERO,Vector2(w,0),Vector2(w,h),Vector2(0,h)])
        building.position = Vector2(bx, 620 - h)
        building.color = Color("#182944") if i % 2 == 0 else Color("#1d304d")
        world.add_child(building)
        for row in range(3):
            for col in range(3):
                var window := Polygon2D.new()
                window.polygon = PackedVector2Array([Vector2.ZERO,Vector2(9,0),Vector2(9,13),Vector2(0,13)])
                window.position = building.position + Vector2(15 + col * 27, 20 + row * 34)
                window.color = Color(accent.r,accent.g,accent.b,0.2 if (i+row+col)%3 else 0.65)
                world.add_child(window)

func _platform(pos: Vector2, size: Vector2) -> void:
    var body := StaticBody2D.new()
    body.position = pos + Vector2(0, size.y * 0.5)
    body.collision_layer = 1
    var collision := CollisionShape2D.new()
    var rect := RectangleShape2D.new()
    rect.size = size
    collision.shape = rect
    body.add_child(collision)
    var art := Polygon2D.new()
    art.polygon = PackedVector2Array([Vector2(-size.x/2, -size.y/2),Vector2(size.x/2, -size.y/2),Vector2(size.x/2,size.y/2),Vector2(-size.x/2,size.y/2)])
    art.color = Color("#273d5a")
    body.add_child(art)
    var trim := Polygon2D.new()
    trim.polygon = PackedVector2Array([Vector2(-size.x/2,-size.y/2),Vector2(size.x/2,-size.y/2),Vector2(size.x/2,-size.y/2+6),Vector2(-size.x/2,-size.y/2+6)])
    trim.color = Color(str(LEVELS[level_index].accent))
    body.add_child(trim)
    world.add_child(body)

func _coin(pos: Vector2) -> void:
    var area := Area2D.new()
    area.position = pos
    area.collision_layer = 0
    area.collision_mask = 1
    var shape := CollisionShape2D.new()
    var circle := CircleShape2D.new()
    circle.radius = 15
    shape.shape = circle
    area.add_child(shape)
    var art := Polygon2D.new()
    art.polygon = PackedVector2Array([Vector2(0,-14),Vector2(10,-7),Vector2(10,7),Vector2(0,14),Vector2(-10,7),Vector2(-10,-7)])
    art.color = Color("#ffda79")
    area.add_child(art)
    area.body_entered.connect(func(body: Node2D):
        if body == player:
            coins += 1
            level_coins += 1
            area.queue_free()
            _update_hud()
    )
    world.add_child(area)

func _hazard(pos: Vector2, width: float) -> void:
    var area := Area2D.new()
    area.position = pos
    area.collision_mask = 1
    var shape := CollisionShape2D.new()
    var rect := RectangleShape2D.new()
    rect.size = Vector2(width, 28)
    shape.shape = rect
    area.add_child(shape)
    var spikes := Polygon2D.new()
    var points := PackedVector2Array()
    var count := int(width / 14)
    for i in range(count):
        var sx := -width/2 + float(i) * width / count
        points.append(Vector2(sx, 12))
        points.append(Vector2(sx + width / count / 2, -13))
        points.append(Vector2(sx + width / count, 12))
    spikes.polygon = points
    spikes.color = Color("#ff6f76")
    area.add_child(spikes)
    area.body_entered.connect(func(body: Node2D):
        if body == player and player.invulnerable <= 0.0:
            screen_shake = 0.25
            _respawn()
    )
    world.add_child(area)

func _checkpoint_flag(pos: Vector2) -> void:
    var area := Area2D.new()
    area.position = pos
    area.collision_mask = 1
    var shape := CollisionShape2D.new()
    var rect := RectangleShape2D.new()
    rect.size = Vector2(48, 86)
    shape.shape = rect
    area.add_child(shape)
    var pole := Polygon2D.new()
    pole.polygon = PackedVector2Array([Vector2(-3,-35),Vector2(3,-35),Vector2(3,30),Vector2(-3,30)])
    pole.color = Color("#d6e0ec")
    area.add_child(pole)
    var flag := Polygon2D.new()
    flag.polygon = PackedVector2Array([Vector2(3,-34),Vector2(31,-26),Vector2(3,-17)])
    flag.color = Color("#ffbd63")
    area.add_child(flag)
    area.body_entered.connect(func(body: Node2D):
        if body == player:
            checkpoint = pos + Vector2(0, 18)
            flag.color = Color("#63e5cc")
    )
    world.add_child(area)

func _goal(pos: Vector2) -> void:
    var area := Area2D.new()
    area.position = pos
    area.collision_mask = 1
    var shape := CollisionShape2D.new()
    var circle := CircleShape2D.new()
    circle.radius = 29
    shape.shape = circle
    area.add_child(shape)
    var ring := Polygon2D.new()
    var points := PackedVector2Array()
    for i in range(12):
        var angle := TAU * float(i) / 12.0
        points.append(Vector2(cos(angle),sin(angle))*26)
        points.append(Vector2(cos(angle),sin(angle))*17)
    ring.polygon = points
    ring.color = Color(str(LEVELS[level_index].accent))
    area.add_child(ring)
    area.body_entered.connect(func(body: Node2D):
        if body == player and not completed:
            completed = true
            unlocked = maxi(unlocked, mini(level_index + 1, 11))
            _save_progress()
            _show_complete()
    )
    world.add_child(area)

func _build_hud() -> void:
    _clear_ui()
    var root := Control.new()
    root.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
    ui.add_child(root)
    var top := HBoxContainer.new()
    top.set_anchors_and_offsets_preset(Control.PRESET_TOP_WIDE)
    top.offset_left = 22
    top.offset_right = -22
    top.offset_top = 16
    top.offset_bottom = 82
    root.add_child(top)
    var info: Dictionary = LEVELS[level_index]
    hud_label = _label("%s  /  %02d     ◆ %d" % [str(info.district), level_index+1, level_coins], 17, Color("#f4f0e8"))
    hud_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
    top.add_child(hud_label)
    var spacer := Control.new()
    spacer.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    top.add_child(spacer)
    var skill := _label(str(info.skill).to_upper(), 14, Color(str(info.accent)))
    top.add_child(skill)
    var pause_button := _button("Ⅱ", _toggle_pause)
    pause_button.custom_minimum_size = Vector2(58, 48)
    top.add_child(pause_button)
    _build_touch_controls(root)

func _build_touch_controls(root: Control) -> void:
    if not settings.touch_controls:
        return
    var bottom := HBoxContainer.new()
    bottom.set_anchors_and_offsets_preset(Control.PRESET_BOTTOM_WIDE)
    bottom.offset_left = 20
    bottom.offset_right = -20
    bottom.offset_top = -115
    bottom.offset_bottom = -18
    bottom.add_theme_constant_override("separation", 12)
    root.add_child(bottom)
    var left := _hold_button("◀", "move_left")
    bottom.add_child(left)
    var right := _hold_button("▶", "move_right")
    bottom.add_child(right)
    var spacer := Control.new()
    spacer.size_flags_horizontal = Control.SIZE_EXPAND_FILL
    bottom.add_child(spacer)
    if level_index >= 4:
        bottom.add_child(_hold_button("DASH", "dash"))
    bottom.add_child(_hold_button("JUMP", "jump", true))

func _hold_button(text: String, action: String, primary := false) -> Button:
    var button := _button(text, func(): pass, primary)
    button.custom_minimum_size = Vector2(78 if text.length() < 4 else 104, 74)
    button.button_down.connect(func(): Input.action_press(action))
    button.button_up.connect(func(): Input.action_release(action))
    button.mouse_exited.connect(func(): Input.action_release(action))
    return button

func _update_hud() -> void:
    if hud_label and is_instance_valid(hud_label):
        var info: Dictionary = LEVELS[level_index]
        hud_label.text = "%s  /  %02d     ◆ %d" % [str(info.district), level_index+1, level_coins]

func _toggle_pause() -> void:
    if not world or completed:
        return
    paused = not paused
    get_tree().paused = paused
    if paused:
        _clear_ui()
        var box := _panel(ui, 520)
        box.add_child(_label("TAKE A BREATHER", 30, Color("#ffbd63")))
        box.add_child(_label("Your route is saved at the last flag.", 15, Color("#aebdd0")))
        box.add_child(_button("RESUME", _toggle_pause, true))
        box.add_child(_button("RESTART CHECKPOINT", _respawn))
        box.add_child(_button("SETTINGS", _show_pause_settings))
        box.add_child(_button("QUIT TO TITLE", _quit_to_title))
    else:
        _build_hud()

func _show_pause_settings() -> void:
    _clear_ui()
    var box := _panel(ui, 520)
    box.add_child(_label("SETTINGS", 30, Color("#ffbd63")))
    var touch := CheckButton.new()
    touch.text = "On-screen touch controls"
    touch.button_pressed = settings.touch_controls
    touch.toggled.connect(func(v: bool): settings.touch_controls = v; _save_settings())
    box.add_child(touch)
    var shake := CheckButton.new()
    shake.text = "Impact camera shake"
    shake.button_pressed = settings.screen_shake
    shake.toggled.connect(func(v: bool): settings.screen_shake = v; _save_settings())
    box.add_child(shake)
    box.add_child(_button("BACK TO PAUSE", _toggle_pause))

func _show_complete() -> void:
    get_tree().paused = true
    _clear_ui()
    var box := _panel(ui, 560)
    box.add_child(_label("ROUTE COMPLETE", 34, Color("#ffbd63")))
    box.add_child(_label("%s\nYou found %d rooftop tokens." % [str(LEVELS[level_index].name), level_coins], 18, Color("#c2cede")))
    if level_index < 11:
        box.add_child(_button("NEXT LEVEL", func(): _start_level(level_index + 1), true))
    else:
        box.add_child(_label("CHAPTER ONE COMPLETE", 22, Color("#63e5cc")))
    box.add_child(_button("REPLAY LEVEL", func(): _start_level(level_index)))
    box.add_child(_button("LEVEL MAP", _show_level_select))

func _respawn() -> void:
    if player and is_instance_valid(player):
        player.reset_to(checkpoint)
        if paused:
            paused = false
            get_tree().paused = false
            _build_hud()

func _quit_to_title() -> void:
    paused = false
    get_tree().paused = false
    _clear_world()
    _show_title()

func _save_progress() -> void:
    var config := ConfigFile.new()
    config.load(SAVE_PATH)
    config.set_value("progress", "unlocked", unlocked)
    config.set_value("progress", "coins", coins)
    config.save(SAVE_PATH)

func _load_save() -> void:
    var config := ConfigFile.new()
    if config.load(SAVE_PATH) == OK:
        unlocked = clampi(int(config.get_value("progress", "unlocked", 0)), 0, 11)
        coins = int(config.get_value("progress", "coins", 0))
        settings.touch_controls = bool(config.get_value("settings", "touch", true))
        settings.screen_shake = bool(config.get_value("settings", "shake", true))

func _save_settings() -> void:
    var config := ConfigFile.new()
    config.load(SAVE_PATH)
    config.set_value("settings", "touch", settings.touch_controls)
    config.set_value("settings", "shake", settings.screen_shake)
    config.save(SAVE_PATH)

