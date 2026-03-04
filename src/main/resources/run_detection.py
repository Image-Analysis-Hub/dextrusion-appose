import os
from dextrusion.DeXtrusion import DeXtrusion

report = print
def listen(callback):
    global report
    report = callback

appose_mode = 'task' in globals()
if appose_mode:
    listen(task.update)
else:
    from appose.python_worker import Task
    task = Task()

task.update(f"Starting python process")

# default parameters
talkative = True  ## print info messages
dexter = DeXtrusion(verbose=talkative)

