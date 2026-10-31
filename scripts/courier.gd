extends CharacterBody2D

signal fell

const SPEED := 340.0
const JUMP_VELOCITY := -650.0
const GRAVITY := 1750.0
const COYOTE_TIME := 0.12
const JUMP_BUFFER := 0.13
const DASH_SPEED := 760.0
const DASH_TIME := 0.19

var wall_jump_unlocked := false
var dash_unlocked := false
var bounce_unlocked := false
var spawn_point := Vector2.ZERO
var coyote_left := 0.0
var jump_buffer_left := 0.0
var dash_left := 0.0
var dash_used := false
var facing := 1.0
var invulnerable := 0.0
var cape_color := Color("#ffbd63")

func _ready() -> void:
    queue_redraw()

func _physics_process(delta: float) -> void:
    if invulnerable > 0.0:
        invulnerable -= delta
    if Input.is_action_just_pressed("jump"):
        jump_buffer_left = JUMP_BUFFER
    else:
        jump_buffer_left = maxf(0.0, jump_buffer_left - delta)

    if is_on_floor():
        coyote_left = COYOTE_TIME
        dash_used = false
    else:
        coyote_left = maxf(0.0, coyote_left - delta)

    var direction := Input.get_axis("move_left", "move_right")
    if direction != 0.0:
        facing = signf(direction)

    if dash_unlocked and Input.is_action_just_pressed("dash") and not dash_used:
        dash_used = true
        dash_left = DASH_TIME
        velocity = Vector2(facing * DASH_SPEED, 0.0)
    elif dash_left > 0.0:
        dash_left -= delta
        velocity.y = 0.0
        velocity.x = facing * DASH_SPEED
    else:
        velocity.x = move_toward(velocity.x, direction * SPEED, 2200.0 * delta)
        if not is_on_floor():
            velocity.y += GRAVITY * delta
        if jump_buffer_left > 0.0:
            if coyote_left > 0.0:
                velocity.y = JUMP_VELOCITY
                coyote_left = 0.0
                jump_buffer_left = 0.0
            elif wall_jump_unlocked and is_on_wall():
                velocity.y = JUMP_VELOCITY * 0.9
                velocity.x = -facing * SPEED * 1.25
                facing = -facing
                jump_buffer_left = 0.0

    if Input.is_action_just_released("jump") and velocity.y < -180.0:
        velocity.y *= 0.52

    move_and_slide()
    if global_position.y > 1100.0:
        fell.emit()

func bounce(power: float = 1.0) -> void:
    velocity.y = JUMP_VELOCITY * power
    dash_used = false

func reset_to(point: Vector2) -> void:
    global_position = point
    velocity = Vector2.ZERO
    dash_left = 0.0
    invulnerable = 0.4

func _draw() -> void:
    var blink := invulnerable > 0.0 and int(Time.get_ticks_msec() / 60) % 2 == 0
    if blink:
        return
    # Original compact courier silhouette, drawn in vector shapes.
    draw_colored_polygon(PackedVector2Array([Vector2(-11,-18),Vector2(7,-17),Vector2(13,2),Vector2(9,18),Vector2(-10,18),Vector2(-14,1)]), Color("#f2eee5"))
    draw_circle(Vector2(2,-23), 9.0, Color("#e9b99a"))
    draw_colored_polygon(PackedVector2Array([Vector2(-6,-31),Vector2(8,-34),Vector2(13,-25),Vector2(-9,-25)]), cape_color.darkened(0.13))
    draw_colored_polygon(PackedVector2Array([Vector2(-8,-4),Vector2(-24,8),Vector2(-20,15),Vector2(-3,7)]), cape_color)
    draw_circle(Vector2(5,-23), 1.5, Color("#172135"))
