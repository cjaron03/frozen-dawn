tellraw @s {"text": "[Survival with full EVA + O2]", "color": "aqua", "clickEvent": {"action": "run_command", "value": "/function atmospheric_breach_check:eva"}}
tellraw @s {"text": "[Survival without EVA \u2014 suffocation check]", "color": "aqua", "clickEvent": {"action": "run_command", "value": "/function atmospheric_breach_check:unsuited"}}
tellraw @s {"text": "[Open the east wall \u2014 BREACH]", "color": "aqua", "clickEvent": {"action": "run_command", "value": "/function atmospheric_breach_check:breach"}}
tellraw @s {"text": "[Repair opening \u2014 five-second oxygen recovery]", "color": "aqua", "clickEvent": {"action": "run_command", "value": "/function atmospheric_breach_check:seal"}}
tellraw @s {"text": "[Creative safety]", "color": "aqua", "clickEvent": {"action": "run_command", "value": "/function atmospheric_breach_check:safe"}}
tellraw @s {"text": "Ordinary flames go out together. Torches stay as Spent Torches. Crouch to brace; airflow ends after two seconds. Q drops an item for the airflow check. Relight with flint and steel after recovery.", "color": "aqua"}
tellraw @s {"text": "Cold remains real and independent of suffocation. Use Creative safety if needed. The setup never resets after initialization.", "color": "aqua"}
