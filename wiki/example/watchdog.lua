local watchdog = peripheral.find("watchdog")
if not watchdog then
    error("watchdog peripheral not found (connect the block via a modem)")
end

-- Timeout MUST be greater than the reset interval below, otherwise normal
-- resets arrive too late and the dog fires on its own. 10s leaves a 5s
-- grace window after a hang before the reboot kicks in.
watchdog.setTimeout(10)  -- seconds
watchdog.enable()        -- requires a timeout to be set first

-- Kick loop: reset, wait 5s, repeat. enable() already zeroed the timer.
while true do
    watchdog.reset()
    os.sleep(5)
end